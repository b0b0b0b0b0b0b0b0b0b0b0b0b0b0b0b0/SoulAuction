package bm.b0b0b0.soulAuction.model;

import java.util.UUID;

public record DealHistoryEntry(
        long historyId,
        String action,
        String auctionId,
        long listingId,
        UUID sellerId,
        UUID buyerId,
        int price,
        int tax,
        AuctionEconomyType economyType,
        long createdAtEpochMillis,
        int buyTax,
        String sellerName,
        String buyerName
) {
    public DealHistoryEntry(
            long historyId,
            String action,
            String auctionId,
            long listingId,
            UUID sellerId,
            UUID buyerId,
            int price,
            int tax,
            AuctionEconomyType economyType,
            long createdAtEpochMillis
    ) {
        this(historyId, action, auctionId, listingId, sellerId, buyerId, price, tax, economyType, createdAtEpochMillis, 0, null, null);
    }

    public DealHistoryEntry(
            long historyId,
            String action,
            String auctionId,
            long listingId,
            UUID sellerId,
            UUID buyerId,
            int price,
            int tax,
            AuctionEconomyType economyType,
            long createdAtEpochMillis,
            int buyTax
    ) {
        this(historyId, action, auctionId, listingId, sellerId, buyerId, price, tax, economyType, createdAtEpochMillis, buyTax, null, null);
    }
}
