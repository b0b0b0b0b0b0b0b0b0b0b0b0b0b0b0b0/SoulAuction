package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.AuctionBrowserMenu;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowseFilterState;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
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
    private final AdminAuctionCreateService adminAuctionCreateService;

    public AuctionSearchChatListener(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            AuctionService auctionService,
            MessageService messageService,
            AdminAuctionCreateService adminAuctionCreateService
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.adminAuctionCreateService = adminAuctionCreateService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (adminAuctionCreateService.peek(player.getUniqueId()).isPresent()) {
            return;
        }
        var pending = auctionService.peekPendingChatSearch(player.getUniqueId());
        if (pending.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        AuctionService.PendingChatSearch context = pending.get();
        PluginSchedulers.run(plugin, player, () -> {
            auctionService.consumePendingChatSearch(player.getUniqueId());
            BrowseFilterState previous = auctionService.browseFilterState(player.getUniqueId());
            String appliedQuery;
            if (query.equalsIgnoreCase("-") || query.isBlank()) {
                appliedQuery = null;
                auctionService.setBrowseFilterState(player.getUniqueId(), previous.withSearch(null));
            } else {
                appliedQuery = query;
                auctionService.setBrowseFilterState(player.getUniqueId(), previous.withSearch(query));
            }
            openBrowser(player, context.auctionId());
            sendSearchResultMessage(player, context.auctionId(), appliedQuery);
        });
    }

    private void openBrowser(Player player, String auctionId) {
        AuctionBrowserMenu menu = new AuctionBrowserMenu(
                player.getUniqueId(),
                auctionId,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings(),
                0,
                auctionService.browseFilterState(player.getUniqueId()).searchQuery()
        );
        player.openInventory(menu.getInventory());
    }

    private void sendSearchResultMessage(Player player, String auctionId, String appliedQuery) {
        BrowseFilterState state = auctionService.browseFilterState(player.getUniqueId());
        BrowseFilterState withoutSearch = state.withSearch(null);
        int total = auctionService.count(
                auctionId,
                AuctionCategory.ALL,
                null,
                player.getUniqueId(),
                withoutSearch
        );
        int found = auctionService.count(
                auctionId,
                AuctionCategory.ALL,
                appliedQuery,
                player.getUniqueId(),
                state
        );
        if (appliedQuery == null || appliedQuery.isBlank()) {
            messageService.send(player, 
                    "search-chat-cleared",
                    Map.of("found", String.valueOf(total), "total", String.valueOf(total))
            );
            return;
        }
        messageService.send(player, 
                "search-chat-applied",
                Map.of(
                        "query", appliedQuery,
                        "found", String.valueOf(found),
                        "total", String.valueOf(total)
                )
        );
    }
}
