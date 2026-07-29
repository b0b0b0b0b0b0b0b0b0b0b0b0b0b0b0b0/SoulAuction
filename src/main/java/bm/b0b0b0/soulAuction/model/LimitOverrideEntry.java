package bm.b0b0b0.soulAuction.model;

import java.util.UUID;

public record LimitOverrideEntry(
        UUID playerId,
        String scope,
        int limit
) {
}
