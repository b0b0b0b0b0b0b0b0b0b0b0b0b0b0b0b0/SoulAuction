package bm.b0b0b0.soulAuction.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PluginSchedulers {

    private PluginSchedulers() {
    }

    public static void runGlobal(Plugin plugin, Runnable runnable) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, runnable);
    }

    public static void run(Plugin plugin, Player player, Runnable runnable) {
        player.getScheduler().execute(plugin, runnable, null, 0L);
    }

    public static void runAsync(Plugin plugin, Runnable runnable) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> runnable.run());
    }

    public static void runAsyncTimer(Plugin plugin, long initialDelayTicks, long periodTicks, Runnable runnable) {
        long initialDelayMillis = Math.max(1L, initialDelayTicks * 50L);
        long periodMillis = Math.max(1L, periodTicks * 50L);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> runnable.run(),
                initialDelayMillis,
                periodMillis,
                java.util.concurrent.TimeUnit.MILLISECONDS
        );
    }
}
