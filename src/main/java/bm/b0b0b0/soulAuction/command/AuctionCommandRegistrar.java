package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.SoulAuction;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;

public final class AuctionCommandRegistrar {

    private AuctionCommandRegistrar() {
    }

    public static void registerHandler(SoulAuction plugin) {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            AuctionCommand command = plugin.auctionCommand();
            if (command == null) {
                return;
            }
            event.registrar().register(
                    "ah",
                    "Open auction menu",
                    List.of("ax"),
                    new AuctionPaperCommand(command)
            );
        });
    }
}
