package bm.b0b0b0.soulAuction.repository;

import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlPerItemRepository implements AuctionRepository {

    private final Path directory;
    private final Path metaFile;
    private final ConcurrentMap<Long, AuctionListing> listingsById;
    private final AtomicLong nextId;
    private final ExecutorService ioExecutor;

    public YamlPerItemRepository(Path directory) {
        this.directory = directory;
        this.metaFile = directory.resolve("meta.yml");
        this.listingsById = new ConcurrentHashMap<>();
        this.nextId = new AtomicLong(1L);
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public CompletableFuture<Void> load() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(directory);
                listingsById.clear();
                long maxId = 0L;
                if (Files.exists(metaFile)) {
                    YamlConfiguration meta = YamlConfiguration.loadConfiguration(metaFile.toFile());
                    nextId.set(Math.max(1L, meta.getLong("nextId", 1L)));
                }
                try (var stream = Files.list(directory)) {
                    List<Path> files = stream
                            .filter(path -> path.getFileName().toString().endsWith(".yml"))
                            .filter(path -> !path.getFileName().toString().equals("meta.yml"))
                            .toList();
                    for (Path file : files) {
                        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                        AuctionListing listing = normalize(fromYaml(yaml));
                        listingsById.put(listing.listingId(), listing);
                        if (listing.listingId() > maxId) {
                            maxId = listing.listingId();
                        }
                    }
                }
                nextId.set(Math.max(nextId.get(), maxId + 1L));
                writeMetaSync();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> flush() {
        return CompletableFuture.runAsync(() -> {
            try {
                writeMetaSync();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }, ioExecutor);
    }

    @Override
    public CompletableFuture<Void> close() {
        return flush().whenComplete((unused, throwable) -> ioExecutor.shutdown());
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
                searchText,
                metadataJson
        );
        listingsById.put(listingId, listing);
        CompletableFuture.runAsync(() -> writeListingSync(listing), ioExecutor);
        return listing;
    }

    @Override
    public AuctionListing remove(long listingId) {
        AuctionListing removed = listingsById.remove(listingId);
        if (removed != null) {
            CompletableFuture.runAsync(() -> deleteListingSync(listingId), ioExecutor);
        }
        return removed;
    }

    @Override
    public void putBack(AuctionListing listing) {
        listingsById.put(listing.listingId(), listing);
        CompletableFuture.runAsync(() -> writeListingSync(listing), ioExecutor);
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
        CompletableFuture.runAsync(() -> writeListingSync(updated), ioExecutor);
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

    private void writeListingSync(AuctionListing listing) {
        try {
            Files.createDirectories(directory);
            File file = directory.resolve(listing.listingId() + ".yml").toFile();
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("listingId", listing.listingId());
            yaml.set("auctionId", listing.auctionId());
            yaml.set("sellerId", listing.sellerId().toString());
            yaml.set("sellerName", listing.sellerName());
            yaml.set("price", listing.price());
            yaml.set("economyType", listing.economyType().name());
            yaml.set("createdAtEpochMillis", listing.createdAtEpochMillis());
            yaml.set("itemBase64", listing.itemBase64());
            yaml.set("category", listing.category().name());
            if (listing.searchText() != null) {
                yaml.set("searchText", listing.searchText());
            }
            if (listing.metadataJson() != null) {
                yaml.set("metadataJson", listing.metadataJson());
            }
            yaml.save(file);
            writeMetaSync();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void deleteListingSync(long listingId) {
        try {
            Files.deleteIfExists(directory.resolve(listingId + ".yml"));
            writeMetaSync();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void writeMetaSync() throws IOException {
        YamlConfiguration meta = new YamlConfiguration();
        meta.set("nextId", nextId.get());
        meta.save(metaFile.toFile());
    }

    private AuctionListing fromYaml(YamlConfiguration yaml) {
        return new AuctionListing(
                yaml.getLong("listingId"),
                yaml.getString("auctionId", "global"),
                UUID.fromString(yaml.getString("sellerId")),
                yaml.getString("sellerName", "unknown"),
                yaml.getInt("price"),
                AuctionEconomyType.fromString(yaml.getString("economyType", "VAULT")),
                yaml.getLong("createdAtEpochMillis"),
                yaml.getString("itemBase64", ""),
                AuctionCategory.valueOf(yaml.getString("category", AuctionCategory.OTHER.name())),
                yaml.getString("searchText", null),
                yaml.getString("metadataJson", null)
        );
    }

    private AuctionListing normalize(AuctionListing listing) {
        String auctionId = listing.auctionId() == null || listing.auctionId().isBlank() ? "global" : listing.auctionId();
        AuctionEconomyType economyType = listing.economyType() == null ? AuctionEconomyType.VAULT : listing.economyType();
        return new AuctionListing(
                listing.listingId(),
                auctionId,
                listing.sellerId(),
                listing.sellerName(),
                listing.price(),
                economyType,
                listing.createdAtEpochMillis(),
                listing.itemBase64(),
                listing.category(),
                listing.searchText(),
                listing.metadataJson()
        );
    }
}
