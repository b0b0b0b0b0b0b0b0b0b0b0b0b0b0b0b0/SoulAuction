package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.AuctionBrowserMenu;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.function.Supplier;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionSearchChatListener implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionService auctionService;
    private final MessageService messageService;

    public AuctionSearchChatListener(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.auctionService = auctionService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        var pending = auctionService.consumePendingChatSearch(player.getUniqueId());
        if (pending.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        AuctionService.PendingChatSearch context = pending.get();
        PluginSchedulers.run(plugin, player, () -> {
            if (query.equalsIgnoreCase("-") || query.isBlank()) {
                auctionService.setBrowseFilterState(
                        player.getUniqueId(),
                        auctionService.browseFilterState(player.getUniqueId()).withSearch(null)
                );
            } else {
                auctionService.setBrowseFilterState(
                        player.getUniqueId(),
                        auctionService.browseFilterState(player.getUniqueId()).withSearch(query)
                );
            }
            AuctionBrowserMenu menu = new AuctionBrowserMenu(
                    player.getUniqueId(),
                    context.auctionId(),
                    auctionService,
                    messageService,
                    configSupplier.get().guiGeneralSettings(),
                    0,
                    auctionService.browseFilterState(player.getUniqueId()).searchQuery()
            );
            player.openInventory(menu.getInventory());
            player.sendMessage(messageService.component("search-chat-applied"));
        });
    }
}
