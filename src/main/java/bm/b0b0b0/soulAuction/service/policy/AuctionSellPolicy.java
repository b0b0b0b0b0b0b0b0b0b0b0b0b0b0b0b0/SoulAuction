package bm.b0b0b0.soulAuction.service.policy;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class AuctionSellPolicy {

    private final AuctionRuntimeStorage runtimeStorage;

    public AuctionSellPolicy(AuctionRuntimeStorage runtimeStorage) {
        this.runtimeStorage = runtimeStorage;
    }

    public boolean isMaterialSellForbidden(Material material, AuctionDefinitionSettings definition, AuctionSettings settings) {
        if (settings.security.materialWhitelistMode) {
            return !isAllowedMaterial(material, definition.allowedMaterials);
        }
        return isBlockedMaterial(material, definition.blockedMaterials);
    }

    public boolean isPlayerBlacklisted(UUID playerId, AuctionSettings settings) {
        if (runtimeStorage.isRuntimeBlacklisted(playerId)) {
            return true;
        }
        if (settings.security.playerBlacklist == null) {
            return false;
        }
        for (String raw : settings.security.playerBlacklist) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                if (playerId.equals(UUID.fromString(raw.trim()))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // skip non-uuid entries
            }
        }
        return false;
    }

    public boolean isWorldSellBlocked(Player seller, AuctionSettings settings) {
        if (settings.security.blockedSellWorlds == null || settings.security.blockedSellWorlds.isEmpty()) {
            return false;
        }
        String worldName = seller.getWorld().getName();
        for (String blocked : settings.security.blockedSellWorlds) {
            if (blocked != null && worldName.equalsIgnoreCase(blocked)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSellCooldownActive(UUID sellerId, AuctionSettings settings) {
        int seconds = settings.security.sellCooldownSeconds;
        if (seconds <= 0) {
            return false;
        }
        long last = runtimeStorage.lastSellEpochMillis(sellerId);
        if (last <= 0L) {
            return false;
        }
        return System.currentTimeMillis() - last < seconds * 1000L;
    }

    private boolean isAllowedMaterial(Material material, List<String> allowedMaterials) {
        if (allowedMaterials == null || allowedMaterials.isEmpty()) {
            return false;
        }
        String materialName = material.name();
        for (String allowed : allowedMaterials) {
            if (allowed != null && materialName.equalsIgnoreCase(allowed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedMaterial(Material material, List<String> blockedMaterials) {
        if (blockedMaterials == null || blockedMaterials.isEmpty()) {
            return false;
        }
        String materialName = material.name();
        for (String blocked : blockedMaterials) {
            if (blocked != null && materialName.equalsIgnoreCase(blocked)) {
                return true;
            }
        }
        return false;
    }
}
