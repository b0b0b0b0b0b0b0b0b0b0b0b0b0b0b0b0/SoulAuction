package bm.b0b0b0.soulAuction.model.region;

import java.util.UUID;

public final class RegionPreviewSession {

    public enum EndReason {
        CANCEL,
        EXPIRED,
        LEFT_REGION,
        TELEPORT,
        DISCONNECT,
        REPLACED
    }

    private final UUID playerId;
    private final RegionBounds bounds;
    private final String worldName;
    private final String regionId;
    private final RegionPreviewPlayerState previousState;
    private final long startedEpochMillis;
    private final long expiresEpochMillis;
    private final int generation;
    private volatile boolean restoring;

    public RegionPreviewSession(
            UUID playerId,
            RegionBounds bounds,
            String worldName,
            String regionId,
            RegionPreviewPlayerState previousState,
            long startedEpochMillis,
            long expiresEpochMillis,
            int generation
    ) {
        this.playerId = playerId;
        this.bounds = bounds;
        this.worldName = worldName;
        this.regionId = regionId;
        this.previousState = previousState;
        this.startedEpochMillis = startedEpochMillis;
        this.expiresEpochMillis = expiresEpochMillis;
        this.generation = generation;
    }

    public UUID playerId() {
        return playerId;
    }

    public RegionBounds bounds() {
        return bounds;
    }

    public String worldName() {
        return worldName;
    }

    public String regionId() {
        return regionId;
    }

    public RegionPreviewPlayerState previousState() {
        return previousState;
    }

    public long startedEpochMillis() {
        return startedEpochMillis;
    }

    public long expiresEpochMillis() {
        return expiresEpochMillis;
    }

    public int generation() {
        return generation;
    }

    public boolean restoring() {
        return restoring;
    }

    public void setRestoring(boolean restoring) {
        this.restoring = restoring;
    }

    public boolean containsLocation(org.bukkit.Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!worldName.equalsIgnoreCase(location.getWorld().getName())) {
            return false;
        }
        return bounds.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
