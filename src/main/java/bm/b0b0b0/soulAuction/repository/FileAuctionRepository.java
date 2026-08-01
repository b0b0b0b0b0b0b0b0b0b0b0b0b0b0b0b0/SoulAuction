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
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.plugin.Plugin;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;

public final class FileAuctionRepository implements AuctionRepository {

    private final Plugin plugin;
    private final Path storageFile;
    private final Gson gson;
    private final ConcurrentMap<Long, AuctionListing> listingsById;
    private final AtomicLong nextId;

    public FileAuctionRepository(Plugin plugin, Path storageFile) {
        this.plugin = plugin;
        this.storageFile = storageFile;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.listingsById = new ConcurrentHashMap<>();
        this.nextId = new AtomicLong(1L);
    }

    @Override
    public CompletableFuture<Void> load() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        PluginSchedulers.runAsync(plugin, () -> {
            try {
                if (Files.notExists(storageFile)) {
                    Path parent = storageFile.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    flushSync();
                    future.complete(null);
                    return;
                }
                try (Reader reader = Files.newBufferedReader(storageFile)) {
                    StoragePayload payload = gson.fromJson(reader, StoragePayload.class);
                    listingsById.clear();
                    long maxId = 0L;
                    if (payload != null && payload.listings != null) {
                        for (AuctionListing listing : payload.listings) {
                            AuctionListing normalized = normalize(listing);
                            listingsById.put(normalized.listingId(), normalized);
                            if (normalized.listingId() > maxId) {
                                maxId = normalized.listingId();
                            }
                        }
                    }
                    long suggestedNext = payload != null ? payload.nextId : 1L;
                    nextId.set(Math.max(maxId + 1L, Math.max(1L, suggestedNext)));
                }
                future.complete(null);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> flush() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        PluginSchedulers.runAsync(plugin, () -> {
            try {
                flushSync();
                future.complete(null);
            } catch (Exception exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> close() {
        return flush();
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
            String metadataJson,
            long createdAtEpochMillis
    ) {
        long listingId = nextId.getAndIncrement();
        AuctionListing listing = new AuctionListing(
                listingId,
                auctionId,
                sellerId,
                sellerName,
                price,
                economyType,
                createdAtEpochMillis,
                itemBase64,
                category,
                searchText,
                metadataJson
        );
        listingsById.put(listingId, listing);
        return listing;
    }

    @Override
    public AuctionListing remove(long listingId) {
        return listingsById.remove(listingId);
    }

    @Override
    public void putBack(AuctionListing listing) {
        listingsById.put(listing.listingId(), listing);
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

    private void flushSync() throws IOException {
        Path parent = storageFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StoragePayload payload = new StoragePayload();
        payload.nextId = nextId.get();
        payload.listings = listAll();
        try (Writer writer = Files.newBufferedWriter(storageFile)) {
            gson.toJson(payload, writer);
        }
    }

    private AuctionListing normalize(AuctionListing listing) {
        String auctionId = listing.auctionId();
        if (auctionId == null || auctionId.isBlank()) {
            auctionId = "global";
        }
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

    private static final class StoragePayload {
        private long nextId;
        private List<AuctionListing> listings;
    }
}
