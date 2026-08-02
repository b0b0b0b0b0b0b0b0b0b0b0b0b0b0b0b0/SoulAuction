package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.FakeActivityConfig;
import bm.b0b0b0.soulAuction.gui.admin.AdminAuctionSettingsMenu;
import bm.b0b0b0.soulAuction.gui.admin.AdminAuctionsMenu;
import bm.b0b0b0.soulAuction.gui.admin.AdminGuiAccess;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionSettingsService;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.util.ListingItemEquality;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionGuiListener implements Listener {

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final AdminAuctionCreateService adminAuctionCreateService;
    private final AdminAuctionSettingsService adminAuctionSettingsService;
    private final Supplier<FakeActivityConfig> fakeActivityConfigSupplier;
    private final ConcurrentHashMap<UUID, BukkitTask> browserExpiryRefreshTasks = new ConcurrentHashMap<>();

    public AuctionGuiListener(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            AuctionService auctionService,
            MessageService messageService,
            AdminAuctionCreateService adminAuctionCreateService,
            AdminAuctionSettingsService adminAuctionSettingsService,
            Supplier<FakeActivityConfig> fakeActivityConfigSupplier
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.adminAuctionCreateService = adminAuctionCreateService;
        this.adminAuctionSettingsService = adminAuctionSettingsService;
        this.fakeActivityConfigSupplier = fakeActivityConfigSupplier;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!auctionService.isLoaded()) {
            event.setCancelled(true);
            messageService.send(player, "error-still-loading");
            player.closeInventory();
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AuctionBrowserMenu browserMenu) {
            handleBrowserClick(event, player, browserMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof FavoriteSellersMenu favoriteSellersMenu) {
            handleFavoriteSellersClick(event, player, favoriteSellersMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof FavoriteListingsMenu favoriteListingsMenu) {
            handleFavoriteListingsClick(event, player, favoriteListingsMenu);
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
        if (event.getView().getTopInventory().getHolder(false) instanceof AuctionSellConfirmMenu sellConfirmMenu) {
            handleSellConfirmClick(event, player, sellConfirmMenu);
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
        if (event.getView().getTopInventory().getHolder(false) instanceof AdminAuctionsMenu adminAuctionsMenu) {
            handleAdminAuctionsClick(event, player, adminAuctionsMenu);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AdminAuctionSettingsMenu adminSettingsMenu) {
            handleAdminAuctionSettingsClick(event, player, adminSettingsMenu);
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
        if (event.getView().getTopInventory().getHolder(false) instanceof FavoriteSellersMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof FavoriteListingsMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AdminAuctionsMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AdminAuctionSettingsMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof ContainerPreviewMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder(false) instanceof AuctionSellConfirmMenu) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof AuctionSellMenu sellMenu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean touchesItemSlot = false;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= event.getView().getTopInventory().getSize()) {
                continue;
            }
            if (rawSlot != sellMenu.itemSlot()) {
                event.setCancelled(true);
                return;
            }
            touchesItemSlot = true;
        }
        if (!touchesItemSlot) {
            event.setCancelled(true);
            return;
        }
        PluginSchedulers.runLater(plugin, player, 1L, sellMenu::syncAmountFromItem);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSellMenuClickMonitor(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof AuctionSellMenu sellMenu)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean affectsItemSlot = false;
        if (event.getClickedInventory() == event.getView().getTopInventory()
                && event.getSlot() == sellMenu.itemSlot()) {
            affectsItemSlot = true;
        }
        if (event.getClickedInventory() instanceof PlayerInventory
                && event.isShiftClick()
                && event.getCurrentItem() != null
                && !event.getCurrentItem().isEmpty()) {
            affectsItemSlot = true;
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
            int hotbar = event.getHotbarButton();
            if (hotbar >= 0 && event.getRawSlot() < topSize && event.getRawSlot() == sellMenu.itemSlot()) {
                affectsItemSlot = true;
            }
        }
        if (!affectsItemSlot) {
            return;
        }
        PluginSchedulers.runLater(plugin, player, 1L, sellMenu::syncAmountFromItem);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder(false) instanceof AuctionSellConfirmMenu confirmMenu) {
            if (!confirmMenu.shouldReturnOnClose()) {
                return;
            }
            if (confirmMenu.isHeldReleased()) {
                return;
            }
            returnHeldStack(player, confirmMenu.takeHeldStack());
            return;
        }
        if (!(event.getInventory().getHolder(false) instanceof AuctionSellMenu sellMenu)) {
            InventoryHolder closedHolder = event.getInventory().getHolder(false);
            if (closedHolder instanceof AuctionBrowserMenu) {
                stopBrowserExpiryRefresh(player.getUniqueId());
            }
            if (closedHolder instanceof AuctionBrowserMenu || isAuctionBrowseFlow(closedHolder)) {
                scheduleBrowseSearchClearOnLeave(player);
            }
            return;
        }
        if (!sellMenu.shouldReturnOnClose()) {
            return;
        }
        if (sellMenu.isReservedReleased()) {
            return;
        }
        sellMenu.syncAmountFromItem();
        ItemStack reserved = sellMenu.takeReservedItem();
        returnHeldStack(player, reserved);
    }

    private void returnHeldStack(Player player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    private void scheduleBrowseSearchClearOnLeave(Player player) {
        PluginSchedulers.runLater(plugin, player, 1L, () -> {
            if (auctionService.peekPendingChatSearch(player.getUniqueId()).isPresent()) {
                return;
            }
            InventoryHolder openHolder = player.getOpenInventory().getTopInventory().getHolder(false);
            if (openHolder instanceof AuctionBrowserMenu || isAuctionBrowseFlow(openHolder)) {
                return;
            }
            auctionService.cancelPendingChatSearch(player.getUniqueId());
            auctionService.clearBrowseSearch(player.getUniqueId());
        });
    }

    private static boolean isAuctionBrowseFlow(InventoryHolder holder) {
        return holder instanceof PurchaseConfirmMenu
                || holder instanceof OwnerListingMenu
                || holder instanceof AuctionPriceFilterMenu
                || holder instanceof FavoriteSellersMenu
                || holder instanceof FavoriteListingsMenu
                || holder instanceof ContainerPreviewMenu
                || holder instanceof PlayerHubMenu
                || holder instanceof PlayerRecordsMenu
                || holder instanceof RecentSalesMenu
                || holder instanceof AuctionSellMenu
                || holder instanceof AuctionSellConfirmMenu;
    }

    private void openSellSetup(
            Player player,
            String auctionId,
            ItemStack stack,
            int price,
            int sellAmount
    ) {
        AuctionSellMenu sellMenu = new AuctionSellMenu(
                player.getUniqueId(),
                auctionId,
                auctionService,
                messageService,
                price,
                sellAmount
        );
        if (stack != null && !stack.isEmpty()) {
            sellMenu.putReservedItem(stack);
            sellMenu.syncAmountFromItem();
        }
        PluginSchedulers.run(plugin, player, () -> player.openInventory(sellMenu.getInventory()));
    }

    private void openSellConfirm(Player player, AuctionSellMenu setupMenu) {
        if (!player.getUniqueId().equals(setupMenu.viewerId())) {
            return;
        }
        setupMenu.syncAmountFromItem();
        ItemStack stack = setupMenu.takeReservedItem();
        if (stack == null || stack.isEmpty()) {
            messageService.send(player, "error-sell-menu-no-item");
            return;
        }
        int sellAmount = Math.min(Math.max(1, setupMenu.sellAmount()), stack.getAmount());
        AuctionSellConfirmMenu confirmMenu = new AuctionSellConfirmMenu(
                player.getUniqueId(),
                setupMenu.auctionId(),
                stack,
                setupMenu.price(),
                sellAmount,
                auctionService,
                messageService
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(confirmMenu.getInventory()));
    }

    private void handleBrowserClick(InventoryClickEvent event, Player player, AuctionBrowserMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        int favoritesSlot = configSupplier.get().guiGeneralSettings().favoritesSlot;
        if (slot == favoritesSlot) {
            if (event.isShiftClick() && event.isLeftClick()) {
                menu.click(slot);
                return;
            }
            if (event.isRightClick()) {
                openFavoriteListingsMenu(player, menu.auctionId(), GuiReturnTarget.BROWSER);
                return;
            }
            openFavoriteSellersMenu(player, menu.auctionId(), GuiReturnTarget.BROWSER);
            return;
        }
        if (slot == configSupplier.get().guiGeneralSettings().historySlot) {
            PlayerHubMenu hubMenu = new PlayerHubMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(hubMenu.getInventory()));
            return;
        }
        Long listingId = menu.listingIdAt(slot);
        if (listingId != null) {
            var listing = auctionService.listingById(listingId);
            if (listing == null) {
                messageService.send(player, "error-listing-unavailable");
                menu.refresh();
                return;
            }
            if (event.getClick() == ClickType.MIDDLE && !listing.sellerId().equals(player.getUniqueId())) {
                boolean added = auctionService.toggleFavoriteSeller(player.getUniqueId(), listing.sellerId());
                messageService.send(player,
                        added ? "favorite-added" : "favorite-removed",
                        Map.of("seller", listing.sellerName())
                );
                menu.refresh();
                return;
            }
            if (event.isShiftClick() && event.isLeftClick()) {
                boolean added = auctionService.toggleFavoriteListing(player.getUniqueId(), listing.listingId());
                messageService.send(player, 
                        added ? "favorite-listing-added" : "favorite-listing-removed",
                        Map.of("id", String.valueOf(listing.listingId()))
                );
                menu.refresh();
                return;
            }
            if (event.isRightClick()) {
                ItemStack clicked = event.getView().getTopInventory().getItem(slot);
                if (openContainerPreviewIfPossible(player, menu.auctionId(), clicked)) {
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
            event.getView().setCursor(ItemStack.empty());
            PluginSchedulers.run(plugin, player, () -> player.openInventory(sellMenu.getInventory()));
            return;
        }
        if (slot == configSupplier.get().guiGeneralSettings().searchSlot) {
            if (event.isShiftClick() && event.isLeftClick()) {
                menu.clearSearch();
                return;
            }
        }
        menu.click(slot);
    }

    private void handleFavoriteSellersClick(InventoryClickEvent event, Player player, FavoriteSellersMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isBack(slot)) {
            if (menu.returnTarget() == GuiReturnTarget.HUB) {
                openHub(player, menu.auctionId());
            } else {
                openBrowser(player, menu.auctionId());
            }
            return;
        }
        if (menu.isPrev(slot)) {
            menu.previousPage();
            return;
        }
        if (menu.isNext(slot)) {
            menu.nextPage();
            return;
        }
        UUID sellerId = menu.sellerIdAt(slot);
        if (sellerId == null) {
            return;
        }
        if (auctionService.isFavoriteSeller(player.getUniqueId(), sellerId)) {
            auctionService.toggleFavoriteSeller(player.getUniqueId(), sellerId);
            String name = auctionService.resolveSellerDisplayName(sellerId);
            messageService.send(player, "favorite-removed", Map.of("seller", name));
        }
        menu.refresh();
    }

    private void handleFavoriteListingsClick(InventoryClickEvent event, Player player, FavoriteListingsMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isBack(slot)) {
            if (menu.returnTarget() == GuiReturnTarget.HUB) {
                openHub(player, menu.auctionId());
            } else {
                openBrowser(player, menu.auctionId());
            }
            return;
        }
        if (menu.isPrev(slot)) {
            menu.previousPage();
            return;
        }
        if (menu.isNext(slot)) {
            menu.nextPage();
            return;
        }
        Long listingId = menu.listingIdAt(slot);
        if (listingId == null) {
            return;
        }
        var listing = auctionService.listingById(listingId);
        if (listing == null) {
            messageService.send(player, "error-listing-unavailable");
            menu.refresh();
            return;
        }
        if (event.isShiftClick() && event.isLeftClick()) {
            auctionService.toggleFavoriteListing(player.getUniqueId(), listingId);
            messageService.send(player, "favorite-listing-removed", Map.of("id", String.valueOf(listingId)));
            menu.refresh();
            return;
        }
        if (listing.sellerId().equals(player.getUniqueId())) {
            OwnerListingMenu ownerMenu = new OwnerListingMenu(
                    player.getUniqueId(),
                    listing.auctionId(),
                    listingId,
                    auctionService,
                    messageService
            );
            PluginSchedulers.run(plugin, player, () -> player.openInventory(ownerMenu.getInventory()));
            return;
        }
        PurchaseConfirmMenu confirmMenu = new PurchaseConfirmMenu(
                player.getUniqueId(),
                listing.auctionId(),
                listingId,
                auctionService,
                messageService
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(confirmMenu.getInventory()));
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
        messageService.send(player, 
                "success-bought",
                Map.of("price", auctionService.formatPrice(result.buyerCharge(), result.listing().auctionId(), player))
        );
        Player seller = result.seller();
        if (seller != null) {
            messageService.send(seller, 
                    "success-sold",
                    Map.of("price", auctionService.formatPrice(result.listing().price(), result.listing().auctionId(), player))
            );
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
                messageService.send(player, "error-listing-unavailable");
                openBrowser(player, menu.auctionId());
                return;
            }
            if (result.movedToClaim()) {
                messageService.send(player, "cancelled-to-claim");
            } else {
                messageService.send(player, "cancelled-and-returned");
            }
            openBrowser(player, menu.auctionId());
            return;
        }
        if (menu.isApply(slot)) {
            EditPriceResult result = auctionService.editListingPrice(player, menu.listingId(), menu.editedPrice());
            if (result == EditPriceResult.SUCCESS) {
                messageService.send(player, "owner-price-updated");
                menu.refresh();
                return;
            }
            if (result == EditPriceResult.INVALID_PRICE) {
                messageService.send(player, "error-invalid-price");
                return;
            }
            if (result == EditPriceResult.NOT_OWNER) {
                messageService.send(player, "error-cancel-not-owner");
                openBrowser(player, menu.auctionId());
                return;
            }
            messageService.send(player, "error-listing-unavailable");
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
        if (menu.isFavoriteSellers(event.getSlot())) {
            openFavoriteSellersMenu(player, menu.auctionId(), GuiReturnTarget.HUB);
            return;
        }
        if (menu.isFavoriteListings(event.getSlot())) {
            openFavoriteListingsMenu(player, menu.auctionId(), GuiReturnTarget.HUB);
            return;
        }
        var view = menu.viewAt(event.getSlot());
        if (view == null) {
            return;
        }
        if (view == PlayerHistoryView.RECENT_AUCTION) {
            RecentSalesMenu salesMenu = new RecentSalesMenu(player, menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(salesMenu.getInventory()));
            return;
        }
        PlayerRecordsMenu recordsMenu = new PlayerRecordsMenu(
                player,
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
        int slot = event.getSlot();
        if (menu.isBack(slot)) {
            PlayerHubMenu hubMenu = new PlayerHubMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(hubMenu.getInventory()));
            return;
        }
        if (menu.view() != PlayerHistoryView.SELLING) {
            return;
        }
        Long listingId = menu.listingIdAt(slot);
        if (listingId == null) {
            return;
        }
        var listing = auctionService.listingById(listingId);
        if (listing == null) {
            messageService.send(player, "error-listing-unavailable");
            menu.refresh();
            return;
        }
        if (!listing.sellerId().equals(player.getUniqueId())) {
            return;
        }
        if (event.isRightClick()) {
            ItemStack clicked = event.getView().getTopInventory().getItem(slot);
            if (openContainerPreviewIfPossible(player, listing.auctionId(), clicked)) {
                return;
            }
        }
        OwnerListingMenu ownerMenu = new OwnerListingMenu(
                player.getUniqueId(),
                listing.auctionId(),
                listingId,
                auctionService,
                messageService
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(ownerMenu.getInventory()));
    }

    private void handleAdminAuctionsClick(InventoryClickEvent event, Player player, AdminAuctionsMenu menu) {
        if (!player.getUniqueId().equals(menu.viewerId())) {
            event.setCancelled(true);
            return;
        }
        if (!AdminGuiAccess.canOpenAdminGui(player)) {
            event.setCancelled(true);
            player.closeInventory();
            messageService.send(player, "error-admin-gui-denied");
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isPrev(slot)) {
            reopenAdminAuctions(player, menu.page() - 1);
            return;
        }
        if (menu.isNext(slot)) {
            reopenAdminAuctions(player, menu.page() + 1);
            return;
        }
        if (menu.isCreate(slot)) {
            player.closeInventory();
            adminAuctionCreateService.begin(player, menu.page());
            return;
        }
        String auctionId = menu.auctionIdAt(slot);
        if (auctionId != null) {
            if (event.isRightClick() && !event.isShiftClick()) {
                openAdminAuctionSettings(player, auctionId, menu.page());
                return;
            }
            if (!event.isLeftClick() || event.isShiftClick()) {
                return;
            }
            player.closeInventory();
            openBrowser(player, auctionId);
        }
    }

    private void handleAdminAuctionSettingsClick(InventoryClickEvent event, Player player, AdminAuctionSettingsMenu menu) {
        if (!player.getUniqueId().equals(menu.viewerId())) {
            event.setCancelled(true);
            return;
        }
        if (!AdminGuiAccess.canOpenAdminGui(player)) {
            event.setCancelled(true);
            player.closeInventory();
            messageService.send(player, "error-admin-gui-denied");
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isBack(slot)) {
            reopenAdminAuctions(player, menu.adminListPage());
            return;
        }
        if (menu.isFakeToggle(slot)) {
            String auctionId = menu.auctionId();
            adminAuctionSettingsService.toggleFakeActivity(player, auctionId, () -> {
                openAdminAuctionSettings(player, auctionId, menu.adminListPage());
            });
        }
    }

    private void openAdminAuctionSettings(Player player, String auctionId, int adminListPage) {
        AdminAuctionSettingsMenu settingsMenu = new AdminAuctionSettingsMenu(
                player.getUniqueId(),
                auctionId,
                adminListPage,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings(),
                fakeActivityConfigSupplier,
                configSupplier
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(settingsMenu.getInventory()));
    }

    private void reopenAdminAuctions(Player player, int page) {
        AdminAuctionsMenu next = new AdminAuctionsMenu(
                player.getUniqueId(),
                page,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings()
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(next.getInventory()));
    }

    private void handleSalesMenuClick(InventoryClickEvent event, Player player, RecentSalesMenu menu) {
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        event.setCancelled(true);
        if (menu.isBack(event.getSlot())) {
            PlayerHubMenu hubMenu = new PlayerHubMenu(player.getUniqueId(), menu.auctionId(), auctionService, messageService);
            PluginSchedulers.run(plugin, player, () -> player.openInventory(hubMenu.getInventory()));
        }
    }

    private boolean openContainerPreviewIfPossible(Player player, String auctionId, ItemStack item) {
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
                auctionId,
                container.getInventory().getSize(),
                messageService.component(player.getUniqueId(), "container-preview-title")
        );
        previewMenu.fillFrom(container);
        PluginSchedulers.run(plugin, player, () -> player.openInventory(previewMenu.getInventory()));
        return true;
    }

    private void openHub(Player player, String auctionId) {
        PlayerHubMenu hubMenu = new PlayerHubMenu(player.getUniqueId(), auctionId, auctionService, messageService);
        PluginSchedulers.run(plugin, player, () -> player.openInventory(hubMenu.getInventory()));
    }

    private void openFavoriteSellersMenu(Player player, String auctionId, GuiReturnTarget returnTarget) {
        FavoriteSellersMenu favoriteMenu = new FavoriteSellersMenu(
                player.getUniqueId(),
                auctionId,
                0,
                returnTarget,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings()
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(favoriteMenu.getInventory()));
    }

    private void openFavoriteListingsMenu(Player player, String auctionId, GuiReturnTarget returnTarget) {
        FavoriteListingsMenu favoriteMenu = new FavoriteListingsMenu(
                player.getUniqueId(),
                auctionId,
                0,
                returnTarget,
                auctionService,
                messageService,
                configSupplier.get().guiGeneralSettings()
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(favoriteMenu.getInventory()));
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
        PluginSchedulers.run(plugin, player, () -> {
            player.openInventory(browserMenu.getInventory());
            startBrowserExpiryRefresh(player, browserMenu);
        });
    }

    private void startBrowserExpiryRefresh(Player player, AuctionBrowserMenu menu) {
        stopBrowserExpiryRefresh(player.getUniqueId());
        if (!auctionService.listingExpiryEnabled(menu.auctionId())) {
            return;
        }
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopBrowserExpiryRefresh(player.getUniqueId());
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder(false) instanceof AuctionBrowserMenu open)) {
                stopBrowserExpiryRefresh(player.getUniqueId());
                return;
            }
            if (!open.viewerId().equals(menu.viewerId()) || !open.auctionId().equalsIgnoreCase(menu.auctionId())) {
                return;
            }
            open.refresh();
        }, 20L, 20L);
        browserExpiryRefreshTasks.put(player.getUniqueId(), task);
    }

    private void stopBrowserExpiryRefresh(UUID playerId) {
        BukkitTask task = browserExpiryRefreshTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    private void handleSellClick(InventoryClickEvent event, Player player, AuctionSellMenu menu) {
        if (!player.getUniqueId().equals(menu.viewerId())) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            return;
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK
                || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getSlot();
        AuctionSellMenu.MenuAction action = menu.clickTop(slot);
        if (action == AuctionSellMenu.MenuAction.ITEM_SLOT) {
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                ItemStack cursor = event.getCursor();
                if (menu.hasBackingStack() && cursor != null && !cursor.isEmpty()) {
                    ItemStack reserved = menu.reservedItem();
                    if (reserved != null && !ListingItemEquality.matches(reserved, cursor)) {
                        event.setCancelled(true);
                        return;
                    }
                }
                if (menu.hasBackingStack()) {
                    boolean taking = cursor == null || cursor.isEmpty();
                    if (taking) {
                        event.setCancelled(true);
                        menu.refresh();
                        return;
                    }
                }
            }
            PluginSchedulers.runLater(plugin, player, 1L, menu::syncAmountFromItem);
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
        if (action == AuctionSellMenu.MenuAction.TO_CONFIRM) {
            menu.syncAmountFromItem();
            openSellConfirm(player, menu);
            return;
        }
    }

    private void handleSellConfirmClick(InventoryClickEvent event, Player player, AuctionSellConfirmMenu menu) {
        if (!player.getUniqueId().equals(menu.viewerId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() instanceof PlayerInventory) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK
                || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getSlot();
        if (menu.isExit(slot)) {
            ItemStack stack = menu.takeHeldStack();
            returnHeldStack(player, stack);
            openBrowser(player, menu.auctionId());
            return;
        }
        if (menu.isNo(slot)) {
            ItemStack stack = menu.takeHeldStack();
            openSellSetup(player, menu.auctionId(), stack, menu.price(), menu.sellAmount());
            return;
        }
        if (!menu.isYes(slot)) {
            return;
        }
        ItemStack stack = menu.takeHeldStack();
        if (stack == null || stack.isEmpty()) {
            messageService.send(player, "error-sell-menu-no-item");
            openBrowser(player, menu.auctionId());
            return;
        }
        int sellAmount = Math.min(menu.sellAmount(), stack.getAmount());
        ItemStack sold = stack.clone();
        sold.setAmount(sellAmount);
        if (!ListingItemEquality.matches(stack, sold)) {
            returnHeldStack(player, stack);
            messageService.send(player, "error-sell-menu-no-item");
            openBrowser(player, menu.auctionId());
            return;
        }
        SellResult result = auctionService.createListingFromEscrow(
                player,
                menu.auctionId(),
                menu.price(),
                stack,
                sellAmount
        );
        if (!result.success()) {
            returnHeldStack(player, stack);
            sendSellError(player, result.failure());
            openSellSetup(player, menu.auctionId(), stack, menu.price(), menu.sellAmount());
            return;
        }
        auctionService.sendListingCreatedMessage(player, result.listing());
        int left = stack.getAmount() - sellAmount;
        if (left > 0) {
            ItemStack remainder = stack.clone();
            remainder.setAmount(left);
            returnHeldStack(player, remainder);
        }
        openBrowser(player, menu.auctionId());
    }

    private void sendPurchaseError(Player player, PurchaseFailure failure) {
        messageService.send(player, failure.messageKey());
    }

    private void sendSellError(Player player, SellFailure failure) {
        messageService.send(player, failure.messageKey());
    }
}
