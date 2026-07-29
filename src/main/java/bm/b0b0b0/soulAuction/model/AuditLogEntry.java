package bm.b0b0b0.soulAuction.model;

import java.util.UUID;

public record AuditLogEntry(
        long auditId,
        long createdAtEpochMillis,
        UUID actorId,
        String actorName,
        String action,
        String details
) {
}
