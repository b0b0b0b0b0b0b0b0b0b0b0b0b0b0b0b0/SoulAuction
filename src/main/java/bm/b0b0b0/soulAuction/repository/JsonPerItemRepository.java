package bm.b0b0b0.soulAuction.repository;

import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
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

public final class JsonPerItemRepository implements AuctionRepository {

    private final Path directory;
    private final Path metaFile;
    private final Gson gson;
    private final ConcurrentMap<Long, AuctionListing> listingsById;
    private final AtomicLong nextId;
    private final ExecutorService ioExecutor;

    public JsonPerItemRepository(Path directory) {
        this.directory = directory;
        this.metaFile = directory.resolve("meta.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
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
                    try (Reader reader = Files.newBufferedReader(metaFile)) {
                        MetaPayload meta = gson.fromJson(reader, MetaPayload.class);
                        if (meta != null && meta.nextId > 0) {
                            nextId.set(meta.nextId);
                        }
                    }
                }
                try (var stream = Files.list(directory)) {
                    List<Path> files = stream
                            .filter(path -> path.getFileName().toString().endsWith(".json"))
                            .filter(path -> !path.getFileName().toString().equals("meta.json"))
                            .toList();
                    for (Path file : files) {
                        try (Reader reader = Files.newBufferedReader(file)) {
                            AuctionListing listing = gson.fromJson(reader, AuctionListing.class);
                            if (listing == null) {
                                continue;
                            }
                            AuctionListing normalized = normalize(listing);
                            listingsById.put(normalized.listingId(), normalized);
                            if (normalized.listingId() > maxId) {
                                maxId = normalized.listingId();
                            }
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
            AuctionCategory category
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
                category
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
                old.category()
        );
        listingsById.put(listingId, updated);
        CompletableFuture.runAsync(() -> writeListingSync(updated), ioExecutor);
        return true;
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
            Path file = directory.resolve(listing.listingId() + ".json");
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(listing, writer);
            }
            writeMetaSync();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void deleteListingSync(long listingId) {
        try {
            Files.deleteIfExists(directory.resolve(listingId + ".json"));
            writeMetaSync();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void writeMetaSync() throws IOException {
        MetaPayload payload = new MetaPayload();
        payload.nextId = nextId.get();
        try (Writer writer = Files.newBufferedWriter(metaFile)) {
            gson.toJson(payload, writer);
        }
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
                listing.category()
        );
    }

    private static final class MetaPayload {
        private long nextId;
    }
}
