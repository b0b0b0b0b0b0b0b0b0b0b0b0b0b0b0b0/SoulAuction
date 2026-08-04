package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.model.region.RegionPreviewSession;
import bm.b0b0b0.soulAuction.service.region.RegionPreviewSessionService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class RegionPreviewListener implements Listener {

    private final RegionPreviewSessionService previewSessions;

    public RegionPreviewListener(RegionPreviewSessionService previewSessions) {
        this.previewSessions = previewSessions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        RegionPreviewSession session = previewSessions.session(player.getUniqueId());
        if (session == null || session.restoring()) {
            return;
        }
        if (event.getTo() == null) {
            return;
        }
        if (sameBlock(event)) {
            return;
        }
        if (session.containsLocation(event.getTo())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        RegionPreviewSession session = previewSessions.session(player.getUniqueId());
        if (session == null || session.restoring()) {
            return;
        }
        if (event.getTo() != null && session.containsLocation(event.getTo())) {
            return;
        }
        previewSessions.endIfPreviewing(player.getUniqueId(), RegionPreviewSession.EndReason.TELEPORT);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!previewSessions.isPreviewing(player.getUniqueId())) {
            return;
        }
        previewSessions.endIfPreviewing(player.getUniqueId(), RegionPreviewSession.EndReason.TELEPORT);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        RegionPreviewSession session = previewSessions.session(player.getUniqueId());
        if (session == null || session.restoring()) {
            return;
        }
        if (event.getNewGameMode() == GameMode.SPECTATOR || event.getNewGameMode() == GameMode.ADVENTURE) {
            return;
        }
        previewSessions.endIfPreviewing(player.getUniqueId(), RegionPreviewSession.EndReason.CANCEL);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!previewSessions.isPreviewing(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (previewSessions.isPreviewing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (previewSessions.isPreviewing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (previewSessions.isPreviewing(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && previewSessions.isPreviewing(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        previewSessions.endSessionOnQuit(event.getPlayer());
    }

    private static boolean sameBlock(PlayerMoveEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return false;
        }
        return event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ();
    }
}
