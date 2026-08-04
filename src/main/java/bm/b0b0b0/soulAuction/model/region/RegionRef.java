package bm.b0b0b0.soulAuction.model.region;

import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.entity.Player;

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

    public static RegionRef resolveForSeller(String input, Player player, WorldGuardBridge bridge) {
        if (input == null || input.isBlank() || player == null || bridge == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.indexOf(':') > 0) {
            return parse(trimmed, null);
        }
        List<RegionRef> owned = bridge.listOwnedRegions(player.getUniqueId());
        List<RegionRef> matches = new ArrayList<>();
        for (RegionRef ref : owned) {
            if (ref.regionId().equalsIgnoreCase(trimmed)) {
                matches.add(ref);
            }
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.size() > 1) {
            String currentWorld = player.getWorld().getName();
            for (RegionRef ref : matches) {
                if (ref.worldName().equalsIgnoreCase(currentWorld)) {
                    return ref;
                }
            }
            return null;
        }
        return parse(trimmed, player.getWorld().getName());
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
