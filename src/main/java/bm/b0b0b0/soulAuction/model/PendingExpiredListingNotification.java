package bm.b0b0b0.soulAuction.model;

import java.util.UUID;

public record PendingExpiredListingNotification(
        UUID playerId,
        String auctionId,
        long sourceListingId,
        long claimId,
        String itemLabel
) {
}
