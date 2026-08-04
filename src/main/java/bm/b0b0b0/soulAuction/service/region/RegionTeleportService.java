package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.region.RegionBounds;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionTeleportService {

    private final JavaPlugin plugin;
    private final WorldGuardBridge worldGuardBridge;
    private final MessageService messageService;
    private final RegionPreviewSessionService previewSessions;
    private final ConcurrentHashMap<UUID, Long> lastTeleportEpochMillis = new ConcurrentHashMap<>();

    public RegionTeleportService(
            JavaPlugin plugin,
            WorldGuardBridge worldGuardBridge,
            MessageService messageService,
            RegionPreviewSessionService previewSessions
    ) {
        this.plugin = plugin;
        this.worldGuardBridge = worldGuardBridge;
        this.messageService = messageService;
        this.previewSessions = previewSessions;
    }

    public RegionPreviewSessionService previewSessions() {
        return previewSessions;
    }

    public boolean teleportToListing(Player player, AuctionListing listing, AuctionSettings.RegionMarketSettings settings) {
        if (player == null || listing == null || settings == null || !settings.previewTeleportEnabled) {
            return false;
        }
        if (!RegionListingHelper.isRegionListing(listing)) {
            messageService.send(player, "region-error-not-region-listing");
            return false;
        }
        if (!worldGuardBridge.available()) {
            messageService.send(player, "region-error-worldguard-unavailable");
            return false;
        }
        if (previewSessions.isPreviewing(player.getUniqueId())) {
            previewSessions.cancel(player);
        }
        int cooldownSeconds = Math.max(0, settings.previewTeleportCooldownSeconds);
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long last = lastTeleportEpochMillis.get(player.getUniqueId());
            if (last != null) {
                long remainingMillis = last + cooldownSeconds * 1000L - now;
                if (remainingMillis > 0) {
                    messageService.send(
                            player,
                            "region-preview-teleport-cooldown",
                            Map.of("seconds", String.valueOf((remainingMillis + 999L) / 1000L))
                    );
                    return false;
                }
            }
        }
        RegionRef region = RegionListingHelper.regionRef(listing);
        RegionBounds bounds = worldGuardBridge.regionBounds(region);
        if (bounds == null) {
            messageService.send(player, "region-preview-teleport-unsafe");
            return false;
        }
        Optional<Location> destination = worldGuardBridge.findSafeVisitLocation(region);
        if (destination.isEmpty()) {
            messageService.send(player, "region-preview-teleport-unsafe");
            return false;
        }
        Location target = destination.get();
        PluginSchedulers.run(plugin, player, () -> {
            player.teleportAsync(target).thenAccept(success -> PluginSchedulers.run(plugin, player, () -> {
                if (!success) {
                    messageService.send(player, "region-preview-teleport-failed");
                    return;
                }
                lastTeleportEpochMillis.put(player.getUniqueId(), System.currentTimeMillis());
                previewSessions.begin(player, region, bounds, settings);
            }));
        });
        return true;
    }
}
