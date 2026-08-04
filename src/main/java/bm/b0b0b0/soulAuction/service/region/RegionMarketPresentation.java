package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import java.util.UUID;

public final class RegionMarketPresentation {

    private RegionMarketPresentation() {
    }

    public static boolean hideWorldName(AuctionSettings.RegionMarketSettings settings) {
        return settings == null || settings.hideWorldName;
    }

    public static String marketTitleKey(UUID viewerId, UUID sellerFilter) {
        if (sellerFilter != null && sellerFilter.equals(viewerId)) {
            return "region-market-my-title";
        }
        return "region-market-title";
    }

    public static String listingTitleKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings) ? "region-listing-title" : "region-listing-title-with-world";
    }

    public static String listingLoreKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings) ? "region-listing-lore" : "region-listing-lore-with-world";
    }

    public static String buyConfirmLoreKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings) ? "region-buy-confirm-lore" : "region-buy-confirm-lore-with-world";
    }

    public static String successPurchaseKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings) ? "region-success-purchase" : "region-success-purchase-with-world";
    }

    public static String announcePurchaseKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings) ? "region-announce-purchase" : "region-announce-purchase-with-world";
    }

    public static String announceListingKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings) ? "region-announce-listing" : "region-announce-listing-with-world";
    }

    public static String announceSaleKey(AuctionSettings.RegionMarketSettings settings) {
        return announcePurchaseKey(settings);
    }

    public static String sellChatRegionKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings) ? "region-sell-chat-region" : "region-sell-chat-region-with-world";
    }

    public static String sellChatInvalidRegionKey(AuctionSettings.RegionMarketSettings settings) {
        return hideWorldName(settings)
                ? "region-sell-chat-invalid-region"
                : "region-sell-chat-invalid-region-with-world";
    }
}
