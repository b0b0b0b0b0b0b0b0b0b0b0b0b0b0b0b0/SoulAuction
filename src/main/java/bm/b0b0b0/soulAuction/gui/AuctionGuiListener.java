package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.result.CancelFailure;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.model.result.ClaimResult;
import bm.b0b0b0.soulAuction.model.result.EditPriceResult;
import bm.b0b0b0.soulAuction.model.result.PurchaseFailure;
import bm.b0b0b0.soulAuction.model.result.PurchaseResult;
import bm.b0b0b0.soulAuction.model.result.SellFailure;
import bm.b0b0b0.soulAuction.model.result.SellResult;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.model.PlayerHistoryView;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionService auctionService;
    private final MessageService messageService;

    public AuctionGuiListener(
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

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AuctionBrowserMenu browserMenu) {
            handleBrowserClick(event, player, browserMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AuctionPriceFilterMenu priceFilterMenu) {
            event.setCancelled(true);
            if (!(event.getClickedInventory() instanceof PlayerInventory)) {
                priceFilterMenu.click(event.getSlot());
            }
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AuctionSellMenu sellMenu) {
            handleSellClick(event, player, sellMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof PurchaseConfirmMenu confirmMenu) {
            handlePurchaseConfirmClick(event, player, confirmMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof OwnerListingMenu ownerMenu) {
            handleOwnerMenuClick(event, player, ownerMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof RecentSalesMenu salesMenu) {
            handleSalesMenuClick(event, player, salesMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof PlayerHubMenu hubMenu) {
            handleHubClick(event, player, hubMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof PlayerRecordsMenu recordsMenu) {
            handleRecordsMenuClick(event, player, recordsMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof ContainerPreviewMenu) {
            if (!(event.getClickedInventory() instanceof PlayerInventory)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof AuctionBrowserMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof ContainerPreviewMenu) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof AuctionSellMenu sellMenu)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= event.getView().getTopInventory().getSize()) {
                continue;
            }
            if (rawSlot != sellMenu.itemSlot()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder(false) instanceof AuctionSellMenu sellMenu)) {
            return;
        }
        ItemStack reserved = sellMenu.takeReservedItem();
        if (reserved == null || reserved.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reserved);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    private void handleBrowserClick(InventoryClickEvent event, Player player, AuctionBrowserMenu menu) {
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (slot == configSupplier.get().guiGeneralSettings().historySlot) {
            PlayerHubMenu hubMenu = new PlayerHubMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(hubMenu.getInventory()));
            return;
        }
        Long listingId = menu.listingIdAt(slot);
        if (listingId != null) {
            var listing = auctionService.listingById(listingId);
            if (listing == null) {
                player.sendMessage(messageService.component("error-listing-unavailable"));
                menu.refresh();
                return;
            }
            if (event.isShiftClick() && event.isRightClick()) {
                boolean added = auctionService.toggleFavoriteSeller(player.getUniqueId(), listing.sellerId());
                player.sendMessage(messageService.component(
                        added ? "favorite-added" : "favorite-removed",
                        Map.of("seller", listing.sellerName())
                ));
                menu.refresh();
                return;
            }
            if (event.isRightClick()) {
                ItemStack clicked = event.getView().getTopInventory().getItem(slot);
                if (openContainerPreviewIfPossible(player, menu, clicked)) {
                    return;
                }
            }
            if (listing.sellerId().equals(player.getUniqueId())) {
                OwnerListingMenu ownerMenu = new OwnerListingMenu(
                        player.getUniqueId(),
                        menu.auctionId(),
                        listingId,
                        auctionService,
                        messageService
                );
                PluginSchedulers.run(plugin, player, () -> player.openInventory(ownerMenu.getInventory()));
                return;
            }
            PurchaseConfirmMenu confirmMenu = new PurchaseConfirmMenu(
                    player.getUniqueId(),
                    menu.auctionId(),
                    listingId,
                    auctionService,
                    messageService
            );
            PluginSchedulers.run(plugin, player, () -> player.openInventory(confirmMenu.getInventory()));
            return;
        }
        if (canStartSellFromSlot(event, slot)) {
            ItemStack cursor = event.getCursor();
            AuctionSellMenu sellMenu = new AuctionSellMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            sellMenu.putReservedItem(cursor.clone());
            sellMenu.syncAmountFromItem();
            event.setCursor(null);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(sellMenu.getInventory()));
            return;
        }
        menu.click(slot);
    }

    private void handlePurchaseConfirmClick(InventoryClickEvent event, Player player, PurchaseConfirmMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isNo(slot)) {
            openBrowser(player, menu.auctionId());
            return;
        }
        if (!menu.isYes(slot)) {
            return;
        }
        PurchaseResult result = auctionService.purchase(player, menu.listingId());
        if (!result.success()) {
            sendPurchaseError(player, result.failure());
            openBrowser(player, menu.auctionId());
            return;
        }
        player.sendMessage(messageService.component(
                "success-bought",
                Map.of("price", auctionService.formatPrice(result.buyerCharge(), result.listing().economyType()))
        ));
        Player seller = result.seller();
        if (seller != null) {
            seller.sendMessage(messageService.component(
                    "success-sold",
                    Map.of("price", auctionService.formatPrice(result.listing().price(), result.listing().economyType()))
            ));
        }
        openBrowser(player, menu.auctionId());
    }

    private void handleOwnerMenuClick(InventoryClickEvent event, Player player, OwnerListingMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isBack(slot)) {
            openBrowser(player, menu.auctionId());
            return;
        }
        if (menu.isRemove(slot)) {
            CancelResult result = auctionService.cancelListing(player, menu.listingId(), false);
            if (!result.success()) {
                player.sendMessage(messageService.component("error-listing-unavailable"));
                openBrowser(player, menu.auctionId());
                return;
            }
            if (result.movedToClaim()) {
                player.sendMessage(messageService.component("cancelled-to-claim"));
            } else {
                player.sendMessage(messageService.component("cancelled-and-returned"));
            }
            openBrowser(player, menu.auctionId());
            return;
        }
        if (menu.isApply(slot)) {
            EditPriceResult result = auctionService.editListingPrice(player, menu.listingId(), menu.editedPrice());
            if (result == EditPriceResult.SUCCESS) {
                player.sendMessage(messageService.component("owner-price-updated"));
                menu.refresh();
                return;
            }
            if (result == EditPriceResult.INVALID_PRICE) {
                player.sendMessage(messageService.component("error-invalid-price"));
                return;
            }
            if (result == EditPriceResult.NOT_OWNER) {
                player.sendMessage(messageService.component("error-cancel-not-owner"));
                openBrowser(player, menu.auctionId());
                return;
            }
            player.sendMessage(messageService.component("error-listing-unavailable"));
            openBrowser(player, menu.auctionId());
            return;
        }
        menu.click(slot);
    }

    private boolean canStartSellFromSlot(InventoryClickEvent event, int slot) {
        if (!event.isLeftClick()) {
            return false;
        }
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.isEmpty()) {
            return false;
        }
        var settings = configSupplier.get().guiGeneralSettings();
        if (slot == settings.previousPageSlot || slot == settings.nextPageSlot
                || slot == settings.historySlot || slot == settings.categorySlot || slot == settings.refreshSlot
                || slot == settings.sortSlot) {
            return false;
        }
        return true;
    }

    private void handleHubClick(InventoryClickEvent event, Player player, PlayerHubMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        if (menu.isBack(event.getSlot())) {
            openBrowser(player, menu.auctionId());
            return;
        }
        var view = menu.viewAt(event.getSlot());
        if (view == null) {
            return;
        }
        if (view == PlayerHistoryView.RECENT_AUCTION) {
            RecentSalesMenu salesMenu = new RecentSalesMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(salesMenu.getInventory()));
            return;
        }
        PlayerRecordsMenu recordsMenu = new PlayerRecordsMenu(
                player.getUniqueId(),
                menu.auctionId(),
                view,
                auctionService,
                messageService
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(recordsMenu.getInventory()));
    }

    private void handleRecordsMenuClick(InventoryClickEvent event, Player player, PlayerRecordsMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        if (menu.isBack(event.getSlot())) {
            PlayerHubMenu hubMenu = new PlayerHubMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(hubMenu.getInventory()));
        }
    }

    private void handleSalesMenuClick(InventoryClickEvent event, Player player, RecentSalesMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        if (menu.isBack(event.getSlot())) {
            openBrowser(player, menu.auctionId());
        }
    }

    private boolean openContainerPreviewIfPossible(Player player, AuctionBrowserMenu menu, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        if (!(item.getItemMeta() instanceof BlockStateMeta stateMeta)) {
            return false;
        }
        if (!(stateMeta.getBlockState() instanceof Container container)) {
            return false;
        }
        ContainerPreviewMenu previewMenu = new ContainerPreviewMenu(
                player.getUniqueId(),
                menu.auctionId(),
                container.getInventory().getSize(),
                messageService.component("container-preview-title")
        );
        previewMenu.fillFrom(container);
        PluginSchedulers.run(plugin, player, () -> player.openInventory(previewMenu.getInventory()));
        return true;
    }

    private void openBrowser(Player player, String auctionId) {
        var prefs = auctionService.consumeBrowsePreferences(player.getUniqueId());
        int page = prefs.map(AuctionService.BrowsePreferences::page).orElse(0);
        String search = prefs.map(AuctionService.BrowsePreferences::searchQuery).orElse(null);
        AuctionBrowserMenu browserMenu = new AuctionBrowserMenu(
                player.getUniqueId(),
                auctionId,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings(),
                page,
                search
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(browserMenu.getInventory()));
    }

    private void handleSellClick(InventoryClickEvent event, Player player, AuctionSellMenu menu) {
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        int slot = event.getSlot();
        AuctionSellMenu.MenuAction action = menu.clickTop(slot);
        if (action == AuctionSellMenu.MenuAction.ITEM_SLOT) {
            PluginSchedulers.run(plugin, player, menu::syncAmountFromItem);
            return;
        }
        event.setCancelled(true);
        if (action == AuctionSellMenu.MenuAction.BACK) {
            AuctionBrowserMenu browserMenu = new AuctionBrowserMenu(
                    player.getUniqueId(),
                    menu.auctionId(),
                    auctionService,
                    messageService,
                    configSupplier.get().guiGeneralSettings()
            );
            PluginSchedulers.run(plugin, player, () -> player.openInventory(browserMenu.getInventory()));
            return;
        }
        if (action == AuctionSellMenu.MenuAction.CONFIRM) {
            menu.syncAmountFromItem();
            int sellAmount = menu.sellAmount();
            ItemStack sold = menu.consumeForListing(sellAmount);
            if (sold == null || sold.isEmpty()) {
                player.sendMessage(messageService.component("error-sell-menu-no-item"));
                return;
            }
            SellResult result = auctionService.createListingFromItem(
                    player,
                    menu.auctionId(),
                    menu.price(),
                    sold,
                    sold.getAmount()
            );
            if (!result.success()) {
                menu.restoreConsumed(sold);
                sendSellError(player, result.failure());
                return;
            }
            player.sendMessage(messageService.component(
                    "success-listed",
                    Map.of("price", auctionService.formatPrice(result.listing().price(), result.listing().economyType()))
            ));
            ItemStack remainder = menu.reservedItem();
            if (remainder != null && !remainder.isEmpty()) {
                Map<Integer, ItemStack> leftovers = player.getInventory().addItem(remainder);
                menu.takeReservedItem();
                leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            }
            AuctionBrowserMenu browserMenu = new AuctionBrowserMenu(
                    player.getUniqueId(),
                    menu.auctionId(),
                    auctionService,
                    messageService,
                    configSupplier.get().guiGeneralSettings()
            );
            PluginSchedulers.run(plugin, player, () -> player.openInventory(browserMenu.getInventory()));
        }
    }

    private void sendPurchaseError(Player player, PurchaseFailure failure) {
        player.sendMessage(messageService.component(failure.messageKey()));
    }

    private void sendSellError(Player player, SellFailure failure) {
        player.sendMessage(messageService.component(failure.messageKey()));
    }
}
