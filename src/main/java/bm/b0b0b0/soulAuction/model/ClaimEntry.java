package bm.b0b0b0.soulAuction.model;

import java.util.UUID;

public record ClaimEntry(
        long claimId,
        UUID ownerId,
        String auctionId,
        long sourceListingId,
        String itemBase64,
        long createdAtEpochMillis,
        String reason
) {
}
