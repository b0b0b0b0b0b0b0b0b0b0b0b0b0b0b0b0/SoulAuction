package bm.b0b0b0.soulAuction.model.region;

import java.util.Locale;
import java.util.Objects;

public record RegionRef(String worldName, String regionId) {

    public RegionRef {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(regionId, "regionId");
        worldName = worldName.trim();
        regionId = regionId.trim();
    }

    public String displayKey() {
        return worldName + ":" + regionId;
    }

    public static RegionRef parse(String input, String defaultWorld) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String trimmed = input.trim();
        int separator = trimmed.indexOf(':');
        if (separator > 0 && separator < trimmed.length() - 1) {
            return new RegionRef(trimmed.substring(0, separator), trimmed.substring(separator + 1));
        }
        if (defaultWorld == null || defaultWorld.isBlank()) {
            return null;
        }
        return new RegionRef(defaultWorld, trimmed);
    }

    public boolean matchesIgnoreCase(RegionRef other) {
        if (other == null) {
            return false;
        }
        return worldName.equalsIgnoreCase(other.worldName) && regionId.equalsIgnoreCase(other.regionId);
    }

    public String normalizedWorld() {
        return worldName.toLowerCase(Locale.ROOT);
    }

    public String normalizedRegionId() {
        return regionId.toLowerCase(Locale.ROOT);
    }
}
