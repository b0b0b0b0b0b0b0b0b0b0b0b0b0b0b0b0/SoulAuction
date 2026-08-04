package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.region.RegionBounds;
import bm.b0b0b0.soulAuction.model.region.RegionPreviewPlayerState;
import bm.b0b0b0.soulAuction.model.region.RegionPreviewSession;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionPreviewSessionService {

    private final JavaPlugin plugin;
    private final MessageService messageService;
    private final ConcurrentHashMap<UUID, RegionPreviewSession> activeSessions = new ConcurrentHashMap<>();
    private int generationCounter;

    public RegionPreviewSessionService(JavaPlugin plugin, MessageService messageService) {
        this.plugin = plugin;
        this.messageService = messageService;
    }

    public boolean isPreviewing(UUID playerId) {
        return playerId != null && activeSessions.containsKey(playerId);
    }

    public RegionPreviewSession session(UUID playerId) {
        return playerId == null ? null : activeSessions.get(playerId);
    }

    public void begin(
            Player player,
            RegionRef region,
            RegionBounds bounds,
            AuctionSettings.RegionMarketSettings settings
    ) {
        if (player == null || bounds == null || region == null || settings == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        endSession(playerId, RegionPreviewSession.EndReason.REPLACED, false);
        int generation = ++generationCounter;
        long now = System.currentTimeMillis();
        int durationSeconds = Math.max(0, settings.previewDurationSeconds);
        long expiresAt = durationSeconds > 0 ? now + durationSeconds * 1000L : Long.MAX_VALUE;
        RegionPreviewPlayerState previousState = RegionPreviewPlayerState.capture(player);
        RegionPreviewSession session = new RegionPreviewSession(
                playerId,
                bounds,
                region.worldName(),
                region.regionId(),
                previousState,
                now,
                expiresAt,
                generation
        );
        activeSessions.put(playerId, session);
        applyPreviewMode(player, settings);
        sendStartedMessage(player, region, durationSeconds);
        if (durationSeconds > 0) {
            scheduleExpiry(player, generation, durationSeconds);
        }
    }

    public boolean cancel(Player player) {
        if (player == null) {
            return false;
        }
        return endSession(player.getUniqueId(), RegionPreviewSession.EndReason.CANCEL, true);
    }

    public void endIfPreviewing(UUID playerId, RegionPreviewSession.EndReason reason) {
        endSession(playerId, reason, true);
    }

    public void endAll() {
        for (UUID playerId : activeSessions.keySet()) {
            endSession(playerId, RegionPreviewSession.EndReason.CANCEL, true);
        }
    }

    public boolean endSession(UUID playerId, RegionPreviewSession.EndReason reason, boolean notify) {
        if (playerId == null) {
            return false;
        }
        RegionPreviewSession session = activeSessions.remove(playerId);
        if (session == null) {
            return false;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return true;
        }
        session.setRestoring(true);
        restorePlayer(player, session.previousState(), shouldRestoreLocation(reason));
        if (notify) {
            sendEndedMessage(player, session, reason);
        }
        return true;
    }

    public void endSessionOnQuit(Player player) {
        if (player == null) {
            return;
        }
        RegionPreviewSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        restoreStateOnly(player, session.previousState());
    }

    private static boolean shouldRestoreLocation(RegionPreviewSession.EndReason reason) {
        return reason == RegionPreviewSession.EndReason.CANCEL
                || reason == RegionPreviewSession.EndReason.EXPIRED
                || reason == RegionPreviewSession.EndReason.REPLACED;
    }

    private void applyPreviewMode(Player player, AuctionSettings.RegionMarketSettings settings) {
        GameMode previewMode = previewGameMode(settings);
        player.setGameMode(previewMode);
        if (previewMode == GameMode.SPECTATOR) {
            return;
        }
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private GameMode previewGameMode(AuctionSettings.RegionMarketSettings settings) {
        if (settings == null || settings.previewSpectatorMode) {
            return GameMode.SPECTATOR;
        }
        return GameMode.ADVENTURE;
    }

    private void restorePlayer(Player player, RegionPreviewPlayerState state, boolean restoreLocation) {
        PluginSchedulers.run(plugin, player, () -> applyRestoredState(player, state, restoreLocation));
    }

    private void restoreStateOnly(Player player, RegionPreviewPlayerState state) {
        if (player.isOnline()) {
            applyRestoredState(player, state, false);
        }
    }

    private void applyRestoredState(Player player, RegionPreviewPlayerState state, boolean restoreLocation) {
        if (restoreLocation) {
            Location target = state.returnLocation();
            if (target.getWorld() != null) {
                player.teleportAsync(target);
            }
        }
        player.setGameMode(state.gameMode());
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.flying());
        player.setFlySpeed(state.flySpeed());
        player.setWalkSpeed(state.walkSpeed());
    }

    private void scheduleExpiry(Player player, int generation, int durationSeconds) {
        long delayTicks = Math.max(1L, durationSeconds * 20L);
        UUID playerId = player.getUniqueId();
        PluginSchedulers.runLater(plugin, player, delayTicks, () -> {
            RegionPreviewSession current = activeSessions.get(playerId);
            if (current == null || current.generation() != generation) {
                return;
            }
            endSession(playerId, RegionPreviewSession.EndReason.EXPIRED, true);
        });
    }

    private void sendStartedMessage(Player player, RegionRef region, int durationSeconds) {
        Component cancelButton = messageService.component(player, "region-preview-cancel-button");
        Map<String, String> placeholders = Map.of(
                "region", region.regionId(),
                "world", region.worldName(),
                "seconds", String.valueOf(Math.max(0, durationSeconds))
        );
        messageService.send(player, "region-preview-started", placeholders, Map.of("cancel_button", cancelButton));
    }

    private void sendEndedMessage(Player player, RegionPreviewSession session, RegionPreviewSession.EndReason reason) {
        String key = switch (reason) {
            case CANCEL -> "region-preview-ended-cancel";
            case EXPIRED -> "region-preview-ended-expired";
            case LEFT_REGION, TELEPORT -> "region-preview-ended-left";
            case DISCONNECT, REPLACED -> null;
        };
        if (key == null) {
            return;
        }
        messageService.send(
                player,
                key,
                Map.of("region", session.regionId(), "world", session.worldName())
        );
    }
}
