package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.command.RegionMarketCommandHandler;
import bm.b0b0b0.soulAuction.command.RegionMarketRouting;
import bm.b0b0b0.soulAuction.command.RegionMarketRouting.ParsedPlayerCommand;
import bm.b0b0b0.soulAuction.region.RegionMarketActivation;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

public final class RegionMarketCommandInterceptListener implements Listener {

    private final Supplier<RegionMarketCommandHandler> commandHandler;

    public RegionMarketCommandInterceptListener(Supplier<RegionMarketCommandHandler> commandHandler) {
        this.commandHandler = commandHandler;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!RegionMarketActivation.worldGuardPresent()) {
            return;
        }
        RegionMarketCommandHandler handler = commandHandler.get();
        if (handler == null) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ParsedPlayerCommand parsed = RegionMarketRouting.parsePlayerCommand(event.getMessage());
        if (parsed == null || !RegionMarketRouting.shouldInterceptWorldGuardRg(parsed.label(), parsed.args())) {
            return;
        }
        event.setCancelled(true);
        handler.handle(player, parsed.args());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!RegionMarketActivation.worldGuardPresent()) {
            return;
        }
        RegionMarketCommandHandler handler = commandHandler.get();
        if (handler == null) {
            return;
        }
        ParsedPlayerCommand parsed = RegionMarketRouting.parsePlayerCommand(event.getBuffer());
        if (parsed == null || !RegionMarketRouting.shouldCompleteWorldGuardRg(parsed.label(), parsed.args())) {
            return;
        }
        List<String> suggestions = handler.tabComplete(event.getSender(), parsed.args());
        if (suggestions == null) {
            return;
        }
        event.getCompletions().clear();
        event.getCompletions().addAll(suggestions);
    }
}
