package bm.b0b0b0.soulAuction.listener;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.admin.AdminAuctionsMenu;
import bm.b0b0b0.soulAuction.gui.admin.AdminGuiAccess;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService.Session;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService.Step;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.function.Supplier;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminAuctionCreateChatListener implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final AdminAuctionCreateService createService;
    private final AuctionService auctionService;
    private final MessageService messageService;

    public AdminAuctionCreateChatListener(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            AdminAuctionCreateService createService,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.createService = createService;
        this.auctionService = auctionService;
        this.messageService = messageService;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (createService.peek(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Session session = createService.peek(player.getUniqueId()).orElseThrow();
        PluginSchedulers.run(plugin, player, () -> {
            if (session.step() == Step.ID) {
                createService.submitId(player, text);
                return;
            }
            int page = session.adminGuiPage();
            createService.submitDisplayName(player, text);
            if (createService.peek(player.getUniqueId()).isEmpty()) {
                reopenAdminGui(player, page);
            }
        });
    }

    public void reopenAdminGui(Player player, int page) {
        if (!AdminGuiAccess.canOpenAdminGui(player)) {
            return;
        }
        if (!auctionService.isLoaded()) {
            messageService.send(player, "error-still-loading");
            return;
        }
        AdminAuctionsMenu menu = new AdminAuctionsMenu(
                player.getUniqueId(),
                page,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings()
        );
        player.openInventory(menu.getInventory());
    }
}
