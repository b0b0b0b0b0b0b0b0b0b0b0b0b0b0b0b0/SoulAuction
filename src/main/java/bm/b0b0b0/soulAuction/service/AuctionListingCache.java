package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.service.browse.AuctionListingSorter;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AuctionListingCache {

    private final Map<String, AuctionBucket> byAuction = new ConcurrentHashMap<>();
    private final Map<Long, AuctionListing> networkUpserts = new ConcurrentHashMap<>();
    private final Set<Long> networkRemovals = ConcurrentHashMap.newKeySet();

    public List<AuctionListing> listingsForAuction(String auctionId, Supplier<List<AuctionListing>> loader) {
        String key = auctionId.toLowerCase(Locale.ROOT);
        AuctionBucket bucket = byAuction.computeIfAbsent(key, ignored -> AuctionBucket.load(loader.get()));
        return bucket.mergeNetwork(networkUpserts, networkRemovals, key);
    }

    public List<AuctionListing> sortedListings(
            String auctionId,
            AuctionSort sort,
            Supplier<List<AuctionListing>> loader,
            PermissionPriorityResolver priorityResolver,
            boolean preSorted
    ) {
        List<AuctionListing> merged = listingsForAuction(auctionId, loader);
        if (!preSorted) {
            return AuctionListingSorter.sort(merged, sort, priorityResolver);
        }
        String key = auctionId.toLowerCase(Locale.ROOT);
        AuctionBucket bucket = byAuction.computeIfAbsent(key, ignored -> AuctionBucket.load(loader.get()));
        return bucket.sorted(sort, priorityResolver, networkUpserts, networkRemovals, key);
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
        for (AuctionBucket bucket : byAuction.values()) {
            total += bucket.rawSize();
        }
        return total;
    }

    public int networkOverlaySize() {
        return networkUpserts.size();
    }

    private static final class AuctionBucket {

        private final List<AuctionListing> raw;
        private final Map<AuctionSort, List<AuctionListing>> sortedByKind = new ConcurrentHashMap<>();

        private AuctionBucket(List<AuctionListing> raw) {
            this.raw = List.copyOf(raw);
        }

        static AuctionBucket load(List<AuctionListing> source) {
            return new AuctionBucket(source == null ? List.of() : source);
        }

        int rawSize() {
            return raw.size();
        }

        List<AuctionListing> mergeNetwork(
                Map<Long, AuctionListing> networkUpserts,
                Set<Long> networkRemovals,
                String auctionKey
        ) {
            if (networkUpserts.isEmpty() && networkRemovals.isEmpty()) {
                return raw;
            }
            Map<Long, AuctionListing> merged = new HashMap<>();
            for (AuctionListing listing : raw) {
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

        List<AuctionListing> sorted(
                AuctionSort sort,
                PermissionPriorityResolver priorityResolver,
                Map<Long, AuctionListing> networkUpserts,
                Set<Long> networkRemovals,
                String auctionKey
        ) {
            List<AuctionListing> merged = mergeNetwork(networkUpserts, networkRemovals, auctionKey);
            if (merged == raw && networkUpserts.isEmpty() && networkRemovals.isEmpty()) {
                return sortedByKind.computeIfAbsent(
                        sort,
                        ignored -> AuctionListingSorter.sort(raw, sort, priorityResolver)
                );
            }
            return AuctionListingSorter.sort(merged, sort, priorityResolver);
        }
    }
}
