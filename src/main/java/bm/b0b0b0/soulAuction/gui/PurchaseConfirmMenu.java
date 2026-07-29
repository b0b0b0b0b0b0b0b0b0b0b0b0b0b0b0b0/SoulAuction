package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class PurchaseConfirmMenu implements InventoryHolder {

    private static final int YES_SLOT = 11;
    private static final int NO_SLOT = 15;

    private final UUID viewerId;
    private final String auctionId;
    private final long listingId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final Inventory inventory;

    public PurchaseConfirmMenu(
            UUID viewerId,
            String auctionId,
            long listingId,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.listingId = listingId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 27, messageService.component("buy-confirm-title"));
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public String auctionId() {
        return auctionId;
    }

    public long listingId() {
        return listingId;
    }

    public void refresh() {
        inventory.clear();
        AuctionListing listing = auctionService.listingById(listingId);
        String price = listing == null ? "?" : auctionService.formatPrice(listing.price(), listing.economyType());
        inventory.setItem(YES_SLOT, button(
                Material.LIME_WOOL,
                messageService.component("buy-confirm-yes"),
                messageService.components("buy-confirm-lore", Map.of("price", price))
        ));
        inventory.setItem(NO_SLOT, button(Material.RED_WOOL, messageService.component("buy-confirm-no"), null));
    }

    public boolean isYes(int slot) {
        return slot == YES_SLOT;
    }

    public boolean isNo(int slot) {
        return slot == NO_SLOT;
    }

    private ItemStack button(Material material, net.kyori.adventure.text.Component title, java.util.List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            itemMeta.displayName(title);
            if (lore != null) {
                itemMeta.lore(lore);
            }
            item.setItemMeta(itemMeta);
        }
        return item;
    }
}
