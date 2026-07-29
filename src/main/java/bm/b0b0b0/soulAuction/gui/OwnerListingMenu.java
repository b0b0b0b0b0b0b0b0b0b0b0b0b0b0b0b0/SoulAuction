package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class OwnerListingMenu implements InventoryHolder {

    private static final int MINUS_SLOT = 11;
    private static final int PLUS_SLOT = 15;
    private static final int APPLY_SLOT = 13;
    private static final int REMOVE_SLOT = 22;
    private static final int BACK_SLOT = 26;

    private final UUID viewerId;
    private final String auctionId;
    private final long listingId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final Inventory inventory;
    private int editedPrice;

    public OwnerListingMenu(
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
        this.inventory = Bukkit.createInventory(this, 27, messageService.component("owner-menu-title"));
        AuctionListing listing = auctionService.listingById(listingId);
        this.editedPrice = listing == null ? 1 : listing.price();
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

    public int editedPrice() {
        return editedPrice;
    }

    public void click(int slot) {
        if (slot == MINUS_SLOT) {
            editedPrice = Math.max(1, editedPrice - 100);
            refresh();
            return;
        }
        if (slot == PLUS_SLOT) {
            Player player = Bukkit.getPlayer(viewerId);
            int max = player == null ? auctionService.globalMaxPrice() : auctionService.maxPrice(player, auctionId);
            editedPrice = Math.min(max, editedPrice + 100);
            refresh();
        }
    }

    public boolean isApply(int slot) {
        return slot == APPLY_SLOT;
    }

    public boolean isRemove(int slot) {
        return slot == REMOVE_SLOT;
    }

    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    public void refresh() {
        inventory.clear();
        AuctionListing listing = auctionService.listingById(listingId);
        String livePrice = listing == null ? "?" : auctionService.formatPrice(listing.price(), listing.economyType());
        inventory.setItem(MINUS_SLOT, button(Material.RED_DYE, messageService.component("owner-price-minus")));
        inventory.setItem(PLUS_SLOT, button(Material.LIME_DYE, messageService.component("owner-price-plus")));
        inventory.setItem(APPLY_SLOT, button(
                Material.NAME_TAG,
                messageService.component("owner-price-apply"),
                messageService.components("owner-price-lore", Map.of(
                        "edited", auctionService.formatPrice(editedPrice, auctionService.economyType(auctionId)),
                        "current", livePrice
                ))
        ));
        inventory.setItem(REMOVE_SLOT, button(Material.BARRIER, messageService.component("owner-remove-listing")));
        inventory.setItem(BACK_SLOT, button(Material.GRAY_DYE, messageService.component("owner-back")));
    }

    private ItemStack button(Material material, net.kyori.adventure.text.Component title) {
        return button(material, title, null);
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
