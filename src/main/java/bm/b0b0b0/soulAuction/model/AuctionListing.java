package bm.b0b0b0.soulAuction.model;

import java.util.UUID;

public record AuctionListing(
        long listingId,
        String auctionId,
        UUID sellerId,
        String sellerName,
        int price,
        AuctionEconomyType economyType,
        long createdAtEpochMillis,
        String itemBase64,
        AuctionCategory category,
        String searchText
) {
    public AuctionListing(
            long listingId,
            String auctionId,
            UUID sellerId,
            String sellerName,
            int price,
            AuctionEconomyType economyType,
            long createdAtEpochMillis,
            String itemBase64,
            AuctionCategory category
    ) {
        this(
                listingId,
                auctionId,
                sellerId,
                sellerName,
                price,
                economyType,
                createdAtEpochMillis,
                itemBase64,
                category,
                null
        );
    }
}
