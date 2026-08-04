package bm.b0b0b0.soulAuction.region;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class RegionMarketActivation {

    private RegionMarketActivation() {
    }

    public static boolean configured(PluginConfig config) {
        if (config == null) {
            return false;
        }
        AuctionSettings settings = config.auctionSettings();
        if (settings == null || settings.regionMarket == null) {
            return false;
        }
        return settings.regionMarket.enabled;
    }

    public static boolean worldGuardPresent() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    public static boolean shouldRun(PluginConfig config) {
        return configured(config) && worldGuardPresent();
    }

    public static String dataDirectory(AuctionSettings.RegionMarketSettings settings) {
        if (settings == null || settings.directory == null || settings.directory.isBlank()) {
            return "regions";
        }
        return settings.directory.trim();
    }
}
