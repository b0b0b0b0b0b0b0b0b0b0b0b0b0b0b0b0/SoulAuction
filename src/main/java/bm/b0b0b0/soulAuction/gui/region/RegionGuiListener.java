package bm.b0b0b0.soulAuction.gui.region;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.result.RegionPurchaseResult;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final RegionMarketService regionMarketService;
    private final MessageService messageService;

    public RegionGuiListener(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            RegionMarketService regionMarketService,
            MessageService messageService
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.regionMarketService = regionMarketService;
        this.messageService = messageService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof RegionMarketMenu menu) {
            handleMarketClick(event, player, menu);
            return;
        }
        if (holder instanceof RegionPurchaseConfirmMenu confirmMenu) {
            handleConfirmClick(event, player, confirmMenu);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof RegionMarketMenu || holder instanceof RegionPurchaseConfirmMenu) {
            event.setCancelled(true);
        }
    }

    private void handleMarketClick(InventoryClickEvent event, Player player, RegionMarketMenu menu) {
        if (!menu.viewerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isSellButton(slot)) {
            AuctionSettings.RegionMarketSettings settings = regionMarketService.settings();
            if (settings == null || !player.hasPermission(settings.sellPermission)) {
                messageService.send(player, "error-no-permission");
                return;
            }
            regionMarketService.sessionService().start(player.getUniqueId());
            player.closeInventory();
            messageService.send(player, "region-sell-chat-region");
            return;
        }
        menu.click(slot);
        Long listingId = menu.listingIdAt(slot);
        if (listingId == null) {
            return;
        }
        AuctionSettings.RegionMarketSettings settings = regionMarketService.settings();
        if (settings == null || !player.hasPermission(settings.buyPermission)) {
            messageService.send(player, "error-no-permission");
            return;
        }
        RegionPurchaseConfirmMenu confirmMenu = new RegionPurchaseConfirmMenu(
                player.getUniqueId(),
                listingId,
                regionMarketService,
                messageService
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(confirmMenu.getInventory()));
    }

    private void handleConfirmClick(InventoryClickEvent event, Player player, RegionPurchaseConfirmMenu menu) {
        if (!menu.viewerId().equals(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isNo(slot)) {
            openMarket(player);
            return;
        }
        if (!menu.isYes(slot)) {
            return;
        }
        RegionPurchaseResult result = regionMarketService.purchase(player, menu.listingId());
        if (!result.success()) {
            messageService.send(player, result.failure().messageKey());
            openMarket(player);
            return;
        }
        messageService.send(
                player,
                "region-success-purchase",
                Map.of(
                        "region", result.listing().metadata().regionId,
                        "world", result.listing().metadata().regionWorld,
                        "price", regionMarketService.formatPrice(result.buyerCharge(), result.listing().auctionId(), player.getUniqueId())
                )
        );
        openMarket(player);
    }

    private void openMarket(Player player) {
        AuctionSettings.RegionMarketSettings regionSettings = regionMarketService.settings();
        RegionMarketMenu menu = new RegionMarketMenu(
                player.getUniqueId(),
                regionMarketService,
                messageService,
                configSupplier.get().guiGeneralSettings(),
                regionSettings
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(menu.getInventory()));
    }
}
