package bm.b0b0b0.soulAuction.gui.region;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.result.PurchaseQuote;
import bm.b0b0b0.soulAuction.service.region.RegionListingHelper;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RegionPurchaseConfirmMenu implements InventoryHolder {

    private static final int YES_SLOT = 11;
    private static final int NO_SLOT = 15;

    private final UUID viewerId;
    private final long listingId;
    private final RegionMarketService regionMarketService;
    private final MessageService messageService;
    private final Inventory inventory;

    public RegionPurchaseConfirmMenu(
            UUID viewerId,
            long listingId,
            RegionMarketService regionMarketService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.listingId = listingId;
        this.regionMarketService = regionMarketService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 27, messageService.component(viewerId, "region-buy-confirm-title"));
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public long listingId() {
        return listingId;
    }

    public void refresh() {
        inventory.clear();
        Player buyer = Bukkit.getPlayer(viewerId);
        PurchaseQuote quote = buyer == null ? null : regionMarketService.quotePurchase(buyer, listingId);
        if (quote == null || quote.listing() == null) {
            inventory.setItem(NO_SLOT, button(Material.RED_WOOL, messageService.component(viewerId, "region-buy-confirm-no"), null));
            return;
        }
        var region = RegionListingHelper.regionRef(quote.listing());
        String price = regionMarketService.formatPrice(quote.totalCharge(), quote.listing().auctionId(), viewerId);
        String basePrice = regionMarketService.formatPrice(quote.listing().price(), quote.listing().auctionId(), viewerId);
        inventory.setItem(YES_SLOT, button(
                Material.LIME_WOOL,
                messageService.component(viewerId, "region-buy-confirm-yes"),
                messageService.components(
                        viewerId,
                        "region-buy-confirm-lore",
                        Map.of(
                                "region", region.regionId(),
                                "world", region.worldName(),
                                "seller", quote.listing().sellerName(),
                                "price", price,
                                "base", basePrice,
                                "buytax", String.valueOf(quote.buyTax())
                        )
                )
        ));
        inventory.setItem(NO_SLOT, button(Material.RED_WOOL, messageService.component(viewerId, "region-buy-confirm-no"), null));
    }

    public boolean isYes(int slot) {
        return slot == YES_SLOT;
    }

    public boolean isNo(int slot) {
        return slot == NO_SLOT;
    }

    private ItemStack button(Material material, net.kyori.adventure.text.Component title, java.util.List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(title);
            if (lore != null) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
