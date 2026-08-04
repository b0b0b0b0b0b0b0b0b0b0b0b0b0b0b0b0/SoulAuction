package bm.b0b0b0.soulAuction.service.policy;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;

public final class AuctionTradeRegionPolicy {

    private final WorldGuardBridge worldGuardBridge;

    public AuctionTradeRegionPolicy() {
        this(new WorldGuardBridge());
    }

    public AuctionTradeRegionPolicy(WorldGuardBridge worldGuardBridge) {
        this.worldGuardBridge = worldGuardBridge;
    }

    public boolean isRestricted(AuctionDefinitionSettings definition) {
        return definition != null
                && definition.worldGuardTradeRegions != null
                && !definition.worldGuardTradeRegions.isEmpty()
                && worldGuardBridge.available();
    }

    public boolean allowsTrade(Player player, AuctionDefinitionSettings definition) {
        if (!isRestricted(definition)) {
            return true;
        }
        return worldGuardBridge.isInAllowedTradeRegions(player, definition.worldGuardTradeRegions);
    }

    public String formattedAllowedRegions(AuctionDefinitionSettings definition) {
        if (definition == null || definition.worldGuardTradeRegions == null || definition.worldGuardTradeRegions.isEmpty()) {
            return "";
        }
        return definition.worldGuardTradeRegions.stream()
                .filter(entry -> entry != null && !entry.isBlank())
                .collect(Collectors.joining(", "));
    }
}
