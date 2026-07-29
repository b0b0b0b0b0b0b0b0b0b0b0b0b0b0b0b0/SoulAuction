package bm.b0b0b0.soulAuction.model;

import java.util.UUID;

public record PendingSaleNotification(
        UUID playerId,
        String auctionId,
        int payout,
        int tax,
        AuctionEconomyType economyType
) {
}
