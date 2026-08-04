package bm.b0b0b0.soulAuction.gui.region;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.model.result.EditDescriptionResult;
import bm.b0b0b0.soulAuction.model.result.EditPriceResult;
import bm.b0b0b0.soulAuction.model.result.RegionPurchaseResult;
import bm.b0b0b0.soulAuction.service.region.RegionListingPresentation;
import bm.b0b0b0.soulAuction.service.region.RegionMarketPresentation;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import bm.b0b0b0.soulAuction.service.region.RegionOwnerEditSessionService;
import bm.b0b0b0.soulAuction.region.RegionMarketPermissions;
import bm.b0b0b0.soulAuction.util.PermissionChecks;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
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
            return;
        }
        if (holder instanceof RegionOwnerListingMenu ownerMenu) {
            handleOwnerClick(event, player, ownerMenu);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof RegionMarketMenu
                || holder instanceof RegionPurchaseConfirmMenu
                || holder instanceof RegionOwnerListingMenu) {
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
            if (settings == null || !PermissionChecks.has(player, RegionMarketPermissions.SELL)) {
                messageService.send(player, "region-error-sell-permission");
                return;
            }
            regionMarketService.sessionService().start(player.getUniqueId());
            player.closeInventory();
            messageService.send(player, RegionMarketPresentation.sellChatRegionKey(regionMarketService.settings()));
            return;
        }
        menu.click(slot);
        Long listingId = menu.listingIdAt(slot);
        if (listingId == null) {
            return;
        }
        AuctionListing listing = regionMarketService.listingById(listingId);
        if (listing == null) {
            return;
        }
        if (event.isRightClick()) {
            regionMarketService.previewTeleport(player, listingId);
            return;
        }
        if (listing.sellerId().equals(player.getUniqueId())) {
            openOwnerMenu(player, listingId, menu.sellerFilter(), menu.page(), menu.sort());
            return;
        }
        AuctionSettings.RegionMarketSettings settings = regionMarketService.settings();
        if (settings == null || !PermissionChecks.has(player, RegionMarketPermissions.BUY)) {
            messageService.send(player, "region-error-buy-permission");
            return;
        }
        RegionPurchaseConfirmMenu confirmMenu = new RegionPurchaseConfirmMenu(
                player.getUniqueId(),
                listingId,
                menu.sellerFilter(),
                menu.page(),
                menu.sort(),
                regionMarketService,
                messageService
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(confirmMenu.getInventory()));
    }

    private void handleOwnerClick(InventoryClickEvent event, Player player, RegionOwnerListingMenu menu) {
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
        if (menu.isBack(slot)) {
            openMarket(player, menu.returnSellerFilter(), menu.returnPage(), menu.returnSort());
            return;
        }
        if (menu.isDescription(slot)) {
            regionMarketService.ownerEditSessionService().startDescriptionEdit(
                    player.getUniqueId(),
                    menu.listingId(),
                    new RegionOwnerEditSessionService.ReturnState(
                            menu.returnSellerFilter(),
                            menu.returnPage(),
                            menu.returnSort()
                    )
            );
            player.closeInventory();
            messageService.send(player, "region-owner-description-chat");
            return;
        }
        if (menu.isRemove(slot)) {
            CancelResult result = regionMarketService.cancelListing(player, menu.listingId(), false);
            if (result.success()) {
                messageService.send(player, "region-success-cancelled");
            } else {
                messageService.send(player, "error-listing-unavailable");
            }
            openMarket(player, menu.returnSellerFilter(), menu.returnPage(), menu.returnSort());
            return;
        }
        if (menu.isApply(slot)) {
            AuctionListing listing = regionMarketService.listingById(menu.listingId());
            String auctionId = listing == null ? "global" : listing.auctionId();
            EditPriceResult result = regionMarketService.editListingPrice(player, menu.listingId(), menu.editedPrice());
            if (result == EditPriceResult.SUCCESS) {
                messageService.send(player, "owner-price-updated", Map.of(
                        "price", regionMarketService.formatPrice(menu.editedPrice(), auctionId, player.getUniqueId())
                ));
                menu.refresh();
            } else if (result == EditPriceResult.INVALID_PRICE) {
                messageService.send(player, "error-invalid-price");
            } else if (result == EditPriceResult.NOT_OWNER) {
                messageService.send(player, "error-not-owner");
            } else {
                messageService.send(player, "error-listing-unavailable");
            }
            return;
        }
        menu.click(slot);
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
            openMarket(player, menu.returnSellerFilter(), menu.returnPage(), menu.returnSort());
            return;
        }
        if (!menu.isYes(slot)) {
            return;
        }
        RegionPurchaseResult result = regionMarketService.purchase(player, menu.listingId());
        if (!result.success()) {
            messageService.send(player, result.failure().messageKey());
            openMarket(player, menu.returnSellerFilter(), menu.returnPage(), menu.returnSort());
            return;
        }
        AuctionListing listing = result.listing();
        messageService.send(
                player,
                RegionMarketPresentation.successPurchaseKey(regionMarketService.settings()),
                RegionListingPresentation.purchasePlaceholders(
                        listing,
                        regionMarketService.formatPrice(result.buyerCharge(), listing.auctionId(), player.getUniqueId()),
                        regionMarketService.worldGuardBridge()
                )
        );
        Player sellerOnline = result.sellerOnline();
        if (sellerOnline != null && sellerOnline.isOnline()) {
            messageService.send(
                    sellerOnline,
                    "region-success-sold",
                    Map.of(
                            "buyer", player.getName(),
                            "region", listing.metadata().regionId,
                            "price", regionMarketService.formatPrice(listing.price(), listing.auctionId(), sellerOnline.getUniqueId())
                    )
            );
        }
        openMarket(player, menu.returnSellerFilter(), menu.returnPage(), menu.returnSort());
    }

    private void openOwnerMenu(Player player, long listingId, UUID sellerFilter, int page, AuctionSort sort) {
        RegionOwnerListingMenu ownerMenu = new RegionOwnerListingMenu(
                player.getUniqueId(),
                listingId,
                sellerFilter,
                page,
                sort,
                regionMarketService,
                messageService
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(ownerMenu.getInventory()));
    }

    private void openMarket(Player player, UUID sellerFilter, int page, AuctionSort sort) {
        AuctionSettings.RegionMarketSettings regionSettings = regionMarketService.settings();
        RegionMarketMenu menu = new RegionMarketMenu(
                player.getUniqueId(),
                regionMarketService,
                messageService,
                configSupplier.get().guiGeneralSettings(),
                regionSettings,
                page,
                sort,
                sellerFilter
        );
        PluginSchedulers.run(plugin, player, () -> player.openInventory(menu.getInventory()));
    }
}
