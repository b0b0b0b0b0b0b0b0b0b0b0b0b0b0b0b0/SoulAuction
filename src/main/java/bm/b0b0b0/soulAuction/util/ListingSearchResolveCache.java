package bm.b0b0b0.soulAuction.util;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class ListingSearchResolveCache {

    private static final ConcurrentHashMap<String, String> BY_KEY = new ConcurrentHashMap<>();

    private ListingSearchResolveCache() {
    }

    public static String resolve(AuctionListing listing, Locale[] locales) {
        String cacheKey = listing.listingId() + '|' + ListingSearchText.localeSignature(locales);
        return BY_KEY.computeIfAbsent(
                cacheKey,
                ignored -> ListingSearchText.buildResolveText(listing, locales)
        );
    }

    public static void invalidate(long listingId) {
        String prefix = Long.toString(listingId) + '|';
        BY_KEY.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void clear() {
        BY_KEY.clear();
    }
}
