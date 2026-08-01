package bm.b0b0b0.soulAuction.bootstrap;

import bm.b0b0b0.soulAuction.SoulAuction;
import org.bstats.bukkit.Metrics;

public final class SoulAuctionMetrics {

    private static final int PLUGIN_ID = 33051;

    private SoulAuctionMetrics() {
    }

    public static void tryStart(SoulAuction plugin, boolean enabled) {
        if (!enabled) {
            return;
        }
        new Metrics(plugin, PLUGIN_ID);
    }
}
