package bm.b0b0b0.soulAuction.repository;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings.DatabaseSettings;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import bm.b0b0b0.soulAuction.model.StorageMode;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SqlAuctionRepository implements AuctionRepository {

    private final StorageMode mode;
    private final DatabaseSettings settings;
    private final Path dataFolder;
    private final ConcurrentMap<Long, AuctionListing> listingsById;
    private final ExecutorService ioExecutor;
    private HikariDataSource dataSource;

    public SqlAuctionRepository(StorageMode mode, DatabaseSettings settings, Path dataFolder) {
        this.mode = mode;
        this.settings = settings;
        this.dataFolder = dataFolder;
        this.listingsById = new ConcurrentHashMap<>();
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public boolean sharedPendingPayouts() {
        return mode == StorageMode.MYSQL;
    }

    @Override
    public void storePendingPayout(PendingSaleNotification notification) {
        if (notification == null || !sharedPendingPayouts()) {
            return;
        }
        CompletableFuture.runAsync(() -> insertPendingPayout(notification), ioExecutor);
    }

    @Override
    public List<PendingSaleNotification> drainPendingPayouts(UUID sellerId) {
        if (sellerId == null || !sharedPendingPayouts()) {
            return List.of();
        }
        try {
            return CompletableFuture.supplyAsync(() -> drainPendingPayoutsSync(sellerId), ioExecutor)
                    .get(8L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return List.of();
        }
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
            String searchText,
            String metadataJson
    ) {
        long listingId = allocateListingIdBlocking();
        if (listingId <= 0L) {
            return null;
        }
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
                searchText,
                metadataJson
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

    public Optional<AuctionListing> claimForSaleBlocking(long listingId, RedisSellGuard redisSellGuard) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> claimForSaleSync(listingId, redisSellGuard),
                    ioExecutor
            ).get(8L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public void restoreActiveBlocking(AuctionListing listing) {
        if (listing == null) {
            return;
        }
        try {
            CompletableFuture.runAsync(() -> restoreActiveSync(listing), ioExecutor).get(8L, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    public Optional<AuctionListing> claimStatusBlocking(long listingId, String newStatus) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> claimStatusSync(listingId, newStatus),
                    ioExecutor
            ).get(8L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public void restoreFromStatusBlocking(AuctionListing listing, String previousStatus) {
        if (listing == null || previousStatus == null || previousStatus.isBlank()) {
            return;
        }
        try {
            CompletableFuture.runAsync(() -> restoreFromStatusSync(listing, previousStatus), ioExecutor)
                    .get(8L, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private Optional<AuctionListing> claimStatusSync(long listingId, String newStatus) {
        AuctionListing snapshot = listingsById.get(listingId);
        if (snapshot == null) {
            return Optional.empty();
        }
        if (!atomicTransition(listingId, "ACTIVE", newStatus)) {
            listingsById.remove(listingId);
            return Optional.empty();
        }
        listingsById.remove(listingId);
        return Optional.of(snapshot);
    }

    private void restoreFromStatusSync(AuctionListing listing, String previousStatus) {
        String sql = "UPDATE soulauction_listings SET status='ACTIVE' WHERE listing_id=? AND status=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, listing.listingId());
            statement.setString(2, previousStatus);
            if (statement.executeUpdate() == 1) {
                listingsById.put(listing.listingId(), listing);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean atomicTransition(long listingId, String fromStatus, String toStatus) {
        String sql = "UPDATE soulauction_listings SET status=? WHERE listing_id=? AND status=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, toStatus);
            statement.setLong(2, listingId);
            statement.setString(3, fromStatus);
            return statement.executeUpdate() == 1;
        } catch (Exception exception) {
            return false;
        }
    }

    private Optional<AuctionListing> claimForSaleSync(long listingId, RedisSellGuard redisSellGuard) {
        if (redisSellGuard != null && !redisSellGuard.tryAcquireListingLockLocal(listingId)) {
            return Optional.empty();
        }
        if (redisSellGuard != null && redisSellGuard.distributedLocksRequired()) {
            if (!redisSellGuard.tryAcquireListingLockDistributed(listingId)) {
                if (redisSellGuard != null) {
                    redisSellGuard.releaseListingLock(listingId);
                }
                return Optional.empty();
            }
        }
        AuctionListing snapshot = listingsById.get(listingId);
        if (snapshot == null) {
            if (redisSellGuard != null) {
                redisSellGuard.releaseListingLock(listingId);
            }
            return Optional.empty();
        }
        if (!atomicMarkSoldIfActive(listingId)) {
            listingsById.remove(listingId);
            if (redisSellGuard != null) {
                redisSellGuard.releaseListingLock(listingId);
            }
            return Optional.empty();
        }
        listingsById.remove(listingId);
        return Optional.of(snapshot);
    }

    private void restoreActiveSync(AuctionListing listing) {
        String sql = "UPDATE soulauction_listings SET status='ACTIVE' WHERE listing_id=? AND status='SOLD'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, listing.listingId());
            if (statement.executeUpdate() == 1) {
                listingsById.put(listing.listingId(), listing);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean atomicMarkSoldIfActive(long listingId) {
        String sql = "UPDATE soulauction_listings SET status='SOLD' WHERE listing_id=? AND status='ACTIVE'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, listingId);
            return statement.executeUpdate() == 1;
        } catch (Exception exception) {
            return false;
        }
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
                old.searchText(),
                old.metadataJson()
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
        ensureColumn("search_text", "TEXT");
        ensureColumn("metadata_json", "TEXT");
        createSequenceTableIfNeeded();
        createPendingPayoutsTableIfNeeded();
    }

    private void createSequenceTableIfNeeded() throws Exception {
        String sql = """
                CREATE TABLE IF NOT EXISTS soulauction_sequence (
                  name VARCHAR(32) PRIMARY KEY,
                  next_value BIGINT NOT NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
        String seed = "INSERT INTO soulauction_sequence (name, next_value) VALUES ('listing', 1)";
        if (mode == StorageMode.SQLITE) {
            seed = "INSERT OR IGNORE INTO soulauction_sequence (name, next_value) VALUES ('listing', 1)";
        } else {
            seed = """
                    INSERT INTO soulauction_sequence (name, next_value) VALUES ('listing', 1)
                    ON DUPLICATE KEY UPDATE name = name
                    """;
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(seed)) {
            statement.executeUpdate();
        }
    }

    private void createPendingPayoutsTableIfNeeded() throws Exception {
        String sql = mode == StorageMode.MYSQL
                ? """
                CREATE TABLE IF NOT EXISTS soulauction_pending_payouts (
                  payout_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  seller_id VARCHAR(36) NOT NULL,
                  auction_id VARCHAR(64) NOT NULL,
                  payout INT NOT NULL,
                  sale_tax INT NOT NULL,
                  economy_type VARCHAR(32) NOT NULL,
                  created_at BIGINT NOT NULL,
                  INDEX idx_pending_seller (seller_id)
                )
                """
                : """
                CREATE TABLE IF NOT EXISTS soulauction_pending_payouts (
                  payout_id INTEGER PRIMARY KEY AUTOINCREMENT,
                  seller_id TEXT NOT NULL,
                  auction_id TEXT NOT NULL,
                  payout INTEGER NOT NULL,
                  sale_tax INTEGER NOT NULL,
                  economy_type TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                )
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private void ensureColumn(String column, String sqlType) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "ALTER TABLE soulauction_listings ADD COLUMN " + column + " " + sqlType
             )) {
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private void loadActiveListings() throws Exception {
        listingsById.clear();
        long maxId = 0L;
        String sql = """
                SELECT listing_id, auction_id, seller_id, seller_name, price, economy_type, created_at, item_base64, category, search_text, metadata_json
                FROM soulauction_listings WHERE status='ACTIVE'
                """;
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
                        AuctionCategory.valueOf(resultSet.getString("category")),
                        resultSet.getString("search_text"),
                        resultSet.getString("metadata_json")
                );
                listingsById.put(listing.listingId(), listing);
                if (listing.listingId() > maxId) {
                    maxId = listing.listingId();
                }
            }
        }
        bumpSequenceAtLeast(maxId + 1L);
    }

    private long allocateListingIdBlocking() {
        try {
            return CompletableFuture.supplyAsync(this::allocateListingIdSync, ioExecutor).get(8L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return -1L;
        }
    }

    private long allocateListingIdSync() {
        if (mode == StorageMode.MYSQL) {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement update = connection.prepareStatement(
                         "UPDATE soulauction_sequence SET next_value = LAST_INSERT_ID(next_value + 1) WHERE name = ?"
                 )) {
                update.setString(1, "listing");
                if (update.executeUpdate() != 1) {
                    return -1L;
                }
                try (PreparedStatement select = connection.prepareStatement("SELECT LAST_INSERT_ID() AS id");
                     ResultSet resultSet = select.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getLong("id");
                    }
                }
            } catch (Exception exception) {
                return -1L;
            }
            return -1L;
        }
        try (Connection connection = dataSource.getConnection()) {
            long id;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT next_value FROM soulauction_sequence WHERE name = ?"
            )) {
                select.setString(1, "listing");
                try (ResultSet resultSet = select.executeQuery()) {
                    if (!resultSet.next()) {
                        return -1L;
                    }
                    id = resultSet.getLong("next_value");
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE soulauction_sequence SET next_value = ? WHERE name = ?"
            )) {
                update.setLong(1, id + 1L);
                update.setString(2, "listing");
                if (update.executeUpdate() != 1) {
                    return -1L;
                }
            }
            return id;
        } catch (Exception exception) {
            return -1L;
        }
    }

    private void bumpSequenceAtLeast(long minimumNext) {
        if (minimumNext <= 1L) {
            return;
        }
        String sql = mode == StorageMode.MYSQL
                ? "UPDATE soulauction_sequence SET next_value = GREATEST(next_value, ?) WHERE name = 'listing'"
                : """
                UPDATE soulauction_sequence
                SET next_value = CASE WHEN next_value < ? THEN ? ELSE next_value END
                WHERE name = 'listing'
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, minimumNext);
            if (mode == StorageMode.SQLITE) {
                statement.setLong(2, minimumNext);
            }
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private void insertPendingPayout(PendingSaleNotification notification) {
        String sql = """
                INSERT INTO soulauction_pending_payouts
                (seller_id, auction_id, payout, sale_tax, economy_type, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, notification.playerId().toString());
            statement.setString(2, notification.auctionId());
            statement.setInt(3, notification.payout());
            statement.setInt(4, notification.tax());
            statement.setString(5, notification.economyType().name());
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private List<PendingSaleNotification> drainPendingPayoutsSync(UUID sellerId) {
        List<PendingSaleNotification> output = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            String selectSql = """
                    SELECT payout_id, auction_id, payout, sale_tax, economy_type
                    FROM soulauction_pending_payouts WHERE seller_id = ? ORDER BY payout_id
                    """;
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement select = connection.prepareStatement(selectSql)) {
                select.setString(1, sellerId.toString());
                try (ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        ids.add(resultSet.getLong("payout_id"));
                        output.add(new PendingSaleNotification(
                                sellerId,
                                resultSet.getString("auction_id"),
                                resultSet.getInt("payout"),
                                resultSet.getInt("sale_tax"),
                                AuctionEconomyType.fromString(resultSet.getString("economy_type"))
                        ));
                    }
                }
            }
            if (!ids.isEmpty()) {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM soulauction_pending_payouts WHERE payout_id = ?"
                )) {
                    for (Long id : ids) {
                        delete.setLong(1, id);
                        delete.addBatch();
                    }
                    delete.executeBatch();
                }
            }
            connection.commit();
        } catch (Exception exception) {
            return List.of();
        }
        return output;
    }

    private void insertListing(AuctionListing listing) {
        String sql = """
                INSERT INTO soulauction_listings
                (listing_id, auction_id, seller_id, seller_name, price, economy_type, created_at, item_base64, category, status, search_text, metadata_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
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
            statement.setString(10, listing.searchText());
            statement.setString(11, listing.metadataJson());
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    private void markSold(long listingId) {
        atomicMarkSoldIfActive(listingId);
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
