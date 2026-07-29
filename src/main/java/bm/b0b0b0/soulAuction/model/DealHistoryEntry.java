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
        long createdAtEpochMillis
) {
}
