package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.SoulAuction;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.region.RegionMarketActivation;
import bm.b0b0b0.soulAuction.region.RegionMarketPermissions;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class RegionMarketPaperCommand implements BasicCommand {

    private final SoulAuction plugin;

    public RegionMarketPaperCommand(SoulAuction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player player)) {
            messageService().send(sender, "error-only-player");
            return;
        }
        RegionMarketCommandHandler handler = plugin.regionMarketCommandHandler();
        if (handler == null) {
            PluginConfig config = plugin.loadedPluginConfig();
            if (config != null && RegionMarketActivation.configured(config) && !RegionMarketActivation.worldGuardPresent()) {
                messageService().send(player, "region-error-worldguard-unavailable");
            } else {
                messageService().send(player, "region-error-disabled");
            }
            return;
        }
        handler.handle(player, args);
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
        CommandSender sender = stack.getSender();
        RegionMarketCommandHandler handler = plugin.regionMarketCommandHandler();
        if (handler == null) {
            return List.of();
        }
        List<String> suggestions = handler.tabComplete(sender, args);
        return suggestions == null ? List.of() : suggestions;
    }

    @Override
    public @NotNull String permission() {
        return RegionMarketPermissions.COMMAND;
    }

    private MessageService messageService() {
        return plugin.messageService();
    }
}
