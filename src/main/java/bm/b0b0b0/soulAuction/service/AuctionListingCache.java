package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AuctionListingCache {

    private final Map<String, List<AuctionListing>> byAuction = new ConcurrentHashMap<>();
    private final Map<Long, AuctionListing> networkUpserts = new ConcurrentHashMap<>();
    private final Set<Long> networkRemovals = ConcurrentHashMap.newKeySet();

    public List<AuctionListing> listingsForAuction(String auctionId, Supplier<List<AuctionListing>> loader) {
        String key = auctionId.toLowerCase(Locale.ROOT);
        List<AuctionListing> base = byAuction.computeIfAbsent(key, ignored -> List.copyOf(loader.get()));
        return mergeNetwork(base, key);
    }

    public void invalidate(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            byAuction.clear();
            return;
        }
        byAuction.remove(auctionId.toLowerCase(Locale.ROOT));
    }

    public void invalidateAll() {
        byAuction.clear();
    }

    public void applyNetworkListing(AuctionListing listing) {
        if (listing == null) {
            return;
        }
        networkRemovals.remove(listing.listingId());
        networkUpserts.put(listing.listingId(), listing);
        invalidate(listing.auctionId());
    }

    public void removeNetworkListing(long listingId, String auctionId) {
        networkUpserts.remove(listingId);
        networkRemovals.add(listingId);
        invalidate(auctionId);
    }

    public void clearNetworkOverlay() {
        networkUpserts.clear();
        networkRemovals.clear();
    }

    public int cachedAuctionBuckets() {
        return byAuction.size();
    }

    public int cachedListingCount() {
        int total = 0;
        for (List<AuctionListing> listings : byAuction.values()) {
            total += listings.size();
        }
        return total;
    }

    public int networkOverlaySize() {
        return networkUpserts.size();
    }

    private List<AuctionListing> mergeNetwork(List<AuctionListing> base, String auctionKey) {
        if (networkUpserts.isEmpty() && networkRemovals.isEmpty()) {
            return base;
        }
        Map<Long, AuctionListing> merged = new HashMap<>();
        for (AuctionListing listing : base) {
            if (!networkRemovals.contains(listing.listingId())) {
                merged.put(listing.listingId(), listing);
            }
        }
        for (AuctionListing listing : networkUpserts.values()) {
            if (listing.auctionId().equalsIgnoreCase(auctionKey)) {
                merged.put(listing.listingId(), listing);
            }
        }
        return List.copyOf(new ArrayList<>(merged.values()));
    }
}
