package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
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
        if (leftovers.isEmpty()) {
            return;
        }
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
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
            RecentSalesMenu historyMenu = new RecentSalesMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(historyMenu.getInventory()));
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
        AuctionService.PurchaseResult result = auctionService.purchase(player, menu.listingId());
        if (!result.success()) {
            sendPurchaseError(player, result.failure());
            openBrowser(player, menu.auctionId());
            return;
        }
        player.sendMessage(messageService.component(
                "success-bought",
                Map.of("price", auctionService.formatPrice(result.listing().price(), result.listing().economyType()))
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
            AuctionService.CancelResult result = auctionService.cancelListing(player, menu.listingId(), false);
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
            AuctionService.EditPriceResult result = auctionService.editListingPrice(player, menu.listingId(), menu.editedPrice());
            if (result == AuctionService.EditPriceResult.SUCCESS) {
                player.sendMessage(messageService.component("owner-price-updated"));
                menu.refresh();
                return;
            }
            if (result == AuctionService.EditPriceResult.INVALID_PRICE) {
                player.sendMessage(messageService.component("error-invalid-price"));
                return;
            }
            if (result == AuctionService.EditPriceResult.NOT_OWNER) {
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
        AuctionBrowserMenu browserMenu = new AuctionBrowserMenu(
                player.getUniqueId(),
                auctionId,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings()
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
            ItemStack reserved = menu.reservedItem();
            if (reserved == null || reserved.isEmpty()) {
                player.sendMessage(messageService.component("error-sell-menu-no-item"));
                return;
            }
            AuctionService.SellResult result = auctionService.createListingFromItem(player, menu.auctionId(), menu.price(), reserved);
            if (!result.success()) {
                sendSellError(player, result.failure());
                return;
            }
            menu.takeReservedItem();
            player.sendMessage(messageService.component(
                    "success-listed",
                    Map.of("price", auctionService.formatPrice(result.listing().price(), result.listing().economyType()))
            ));
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

    private void sendPurchaseError(Player player, AuctionService.PurchaseFailure failure) {
        String key = switch (failure) {
            case LISTING_UNAVAILABLE -> "error-listing-unavailable";
            case AUCTION_NOT_FOUND -> "error-auction-not-found";
            case BUY_DISABLED_IN_AUCTION -> "error-buy-disabled-in-auction";
            case BUY_PERMISSION_DENIED -> "error-buy-auction-denied";
            case ECONOMY_UNAVAILABLE -> "error-economy-unavailable";
            case OWN_LISTING -> "error-own-listing";
            case NOT_ENOUGH_MONEY -> "error-not-enough-money";
            case INVENTORY_FULL -> "error-inventory-full";
        };
        player.sendMessage(messageService.component(key));
    }

    private void sendSellError(Player player, AuctionService.SellFailure failure) {
        String key = switch (failure) {
            case SELL_DISABLED -> "error-sell-disabled";
            case SELL_LOCK_FAILED -> "error-sell-lock-failed";
            case SELL_DISABLED_IN_AUCTION -> "error-sell-disabled-in-auction";
            case SELL_PERMISSION_DENIED -> "error-sell-auction-denied";
            case AUCTION_NOT_FOUND -> "error-auction-not-found";
            case INVALID_PRICE -> "error-invalid-price";
            case ECONOMY_UNAVAILABLE -> "error-economy-unavailable";
            case AUCTION_LIMIT_REACHED -> "error-auction-limit";
            case GLOBAL_LIMIT_REACHED -> "error-global-limit";
            case BLOCKED_ITEM -> "error-blocked-item";
            case EMPTY_HAND -> "error-main-hand-empty";
        };
        player.sendMessage(messageService.component(key));
    }
}
