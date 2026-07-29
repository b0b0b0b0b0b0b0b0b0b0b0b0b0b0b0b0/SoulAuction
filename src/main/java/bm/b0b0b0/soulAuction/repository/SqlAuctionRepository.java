package bm.b0b0b0.soulAuction.repository;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings.DatabaseSettings;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.StorageMode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class SqlAuctionRepository implements AuctionRepository {

    private final StorageMode mode;
    private final DatabaseSettings settings;
    private final Path dataFolder;
    private final ConcurrentMap<Long, AuctionListing> listingsById;
    private final AtomicLong nextId;
    private final ExecutorService ioExecutor;
    private HikariDataSource dataSource;

    public SqlAuctionRepository(StorageMode mode, DatabaseSettings settings, Path dataFolder) {
        this.mode = mode;
        this.settings = settings;
        this.dataFolder = dataFolder;
        this.listingsById = new ConcurrentHashMap<>();
        this.nextId = new AtomicLong(1L);
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            try {
                initDataSource();
                createTableIfNeeded();
                loadActiveListings();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (dataSource != null) {
                    dataSource.close();
                }
            } finally {
                ioExecutor.shutdown();
            }
        }, ioExecutor);
    }

    @Override
    public AuctionListing create(
            String auctionId,
            UUID sellerId,
            String sellerName,
            int price,
            AuctionEconomyType economyType,
            String itemBase64,
            AuctionCategory category,
            String searchText
    ) {
        long listingId = nextId.getAndIncrement();
        AuctionListing listing = new AuctionListing(
                listingId,
                auctionId,
                sellerId,
                sellerName,
                price,
                economyType,
                System.currentTimeMillis(),
                itemBase64,
                category,
                searchText
        );
        listingsById.put(listingId, listing);
        CompletableFuture.runAsync(() -> insertListing(listing), ioExecutor);
        return listing;
    }

    @Override
    public AuctionListing remove(long listingId) {
        AuctionListing removed = listingsById.remove(listingId);
        if (removed != null) {
            CompletableFuture.runAsync(() -> markSold(listingId), ioExecutor);
        }
        return removed;
    }

    @Override
    public void putBack(AuctionListing listing) {
        listingsById.put(listing.listingId(), listing);
        CompletableFuture.runAsync(() -> upsertActive(listing), ioExecutor);
    }

    @Override
    public AuctionListing findById(long listingId) {
        return listingsById.get(listingId);
    }

    @Override
    public boolean updatePrice(long listingId, int newPrice) {
        AuctionListing old = listingsById.get(listingId);
        if (old == null) {
            return false;
        }
        AuctionListing updated = new AuctionListing(
                old.listingId(),
                old.auctionId(),
                old.sellerId(),
                old.sellerName(),
                newPrice,
                old.economyType(),
                old.createdAtEpochMillis(),
                old.itemBase64(),
                old.category(),
                old.searchText()
        );
        listingsById.put(listingId, updated);
        CompletableFuture.runAsync(() -> updatePriceSql(listingId, newPrice), ioExecutor);
        return true;
    }

    @Override
    public List<AuctionListing> listByAuction(String auctionId) {
        List<AuctionListing> output = new ArrayList<>();
        for (AuctionListing listing : listingsById.values()) {
            if (listing.auctionId().equalsIgnoreCase(auctionId)) {
                output.add(listing);
            }
        }
        return output;
    }

    @Override
    public List<AuctionListing> listAll() {
        return new ArrayList<>(listingsById.values());
    }

    @Override
    public int countBySeller(UUID sellerId) {
        int amount = 0;
        for (AuctionListing listing : listingsById.values()) {
            if (listing.sellerId().equals(sellerId)) {
                amount++;
            }
        }
        return amount;
    }

    @Override
    public int countBySellerInAuction(UUID sellerId, String auctionId) {
        int amount = 0;
        for (AuctionListing listing : listingsById.values()) {
            if (listing.sellerId().equals(sellerId) && listing.auctionId().equalsIgnoreCase(auctionId)) {
                amount++;
            }
        }
        return amount;
    }

    private void initDataSource() {
        if (dataSource != null) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("SoulAuctionPool");
        config.setMaximumPoolSize(Math.max(2, settings.poolSize));
        if (mode == StorageMode.SQLITE) {
            Path sqliteFile = dataFolder.resolve(settings.sqliteFile);
            try {
                Path parent = sqliteFile.getParent();
                if (parent != null) {
                    java.nio.file.Files.createDirectories(parent);
                }
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            String sqlitePath = sqliteFile.toString().replace("\\", "/");
            config.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            config.setDriverClassName("org.sqlite.JDBC");
        } else {
            config.setJdbcUrl("jdbc:mysql://" + settings.host + ":" + settings.port + "/" + settings.database + "?useSSL=false&characterEncoding=utf8");
            config.setUsername(settings.username);
            config.setPassword(settings.password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
        dataSource = new HikariDataSource(config);
    }

    private void createTableIfNeeded() throws Exception {
        String sql = """
                CREATE TABLE IF NOT EXISTS soulauction_listings (
                  listing_id BIGINT PRIMARY KEY,
                  auction_id VARCHAR(64) NOT NULL,
                  seller_id VARCHAR(36) NOT NULL,
                  seller_name VARCHAR(64) NOT NULL,
                  price INT NOT NULL,
                  economy_type VARCHAR(32) NOT NULL,
                  created_at BIGINT NOT NULL,
                  item_base64 TEXT NOT NULL,
                  category VARCHAR(32) NOT NULL,
                  status VARCHAR(16) NOT NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private void loadActiveListings() throws Exception {
        listingsById.clear();
        long maxId = 0L;
        String sql = "SELECT listing_id, auction_id, seller_id, seller_name, price, economy_type, created_at, item_base64, category FROM soulauction_listings WHERE status='ACTIVE'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                AuctionListing listing = new AuctionListing(
                        resultSet.getLong("listing_id"),
                        resultSet.getString("auction_id"),
                        UUID.fromString(resultSet.getString("seller_id")),
                        resultSet.getString("seller_name"),
                        resultSet.getInt("price"),
                        AuctionEconomyType.fromString(resultSet.getString("economy_type")),
                        resultSet.getLong("created_at"),
                        resultSet.getString("item_base64"),
                        AuctionCategory.valueOf(resultSet.getString("category"))
                );
                listingsById.put(listing.listingId(), listing);
                if (listing.listingId() > maxId) {
                    maxId = listing.listingId();
                }
            }
        }
        nextId.set(Math.max(nextId.get(), maxId + 1L));
    }

    private void insertListing(AuctionListing listing) {
        String sql = """
                INSERT INTO soulauction_listings
                (listing_id, auction_id, seller_id, seller_name, price, economy_type, created_at, item_base64, category, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, listing.listingId());
            statement.setString(2, listing.auctionId());
            statement.setString(3, listing.sellerId().toString());
            statement.setString(4, listing.sellerName());
            statement.setInt(5, listing.price());
            statement.setString(6, listing.economyType().name());
            statement.setLong(7, listing.createdAtEpochMillis());
            statement.setString(8, listing.itemBase64());
            statement.setString(9, listing.category().name());
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private void markSold(long listingId) {
        String sql = "UPDATE soulauction_listings SET status='SOLD' WHERE listing_id=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, listingId);
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private void upsertActive(AuctionListing listing) {
        markSold(listing.listingId());
        insertListing(listing);
    }

    private void updatePriceSql(long listingId, int newPrice) {
        String sql = "UPDATE soulauction_listings SET price=? WHERE listing_id=? AND status='ACTIVE'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, newPrice);
            statement.setLong(2, listingId);
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }
}
