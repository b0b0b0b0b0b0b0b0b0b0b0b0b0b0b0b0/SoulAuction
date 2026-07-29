package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class AuctionListingCache {

    private final Map<String, List<AuctionListing>> byAuction = new ConcurrentHashMap<>();

    public List<AuctionListing> listingsForAuction(String auctionId, Supplier<List<AuctionListing>> loader) {
        String key = auctionId.toLowerCase(Locale.ROOT);
        return byAuction.computeIfAbsent(key, ignored -> List.copyOf(loader.get()));
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
}
