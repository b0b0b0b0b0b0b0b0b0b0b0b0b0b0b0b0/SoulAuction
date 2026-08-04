package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.SoulAuction;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.region.RegionMarketActivation;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;

public final class RegionMarketCommandRegistrar {

    private RegionMarketCommandRegistrar() {
    }

    public static void register(SoulAuction plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            PluginConfig config = plugin.loadedPluginConfig();
            AuctionSettings.RegionMarketSettings regionMarket = config == null
                    ? new AuctionSettings().regionMarket
                    : config.auctionSettings().regionMarket;
            boolean worldGuardPresent = RegionMarketActivation.worldGuardPresent();
            List<String> commands = RegionMarketRouting.normalizedStandaloneCommands(regionMarket, worldGuardPresent);
            RegionMarketPaperCommand command = new RegionMarketPaperCommand(plugin);
            for (String name : commands) {
                event.registrar().register(
                        name,
                        "SoulAuction region market",
                        List.of(),
                        command
                );
            }
        });
    }
}
