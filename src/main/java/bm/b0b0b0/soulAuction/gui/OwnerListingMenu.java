package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.ListingItemPresentation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class OwnerListingMenu implements InventoryHolder {

    private static final int ITEM_SLOT = 22;
    private static final int PRICE_SLOT = 31;
    private static final int MINUS_SMALL_SLOT = 28;
    private static final int MINUS_BIG_SLOT = 29;
    private static final int PLUS_SMALL_SLOT = 33;
    private static final int PLUS_BIG_SLOT = 34;
    private static final int REMOVE_SLOT = 20;
    private static final int BACK_SLOT = 45;
    private static final int APPLY_SLOT = 53;

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
        this.inventory = Bukkit.createInventory(this, 54, messageService.component(viewerId, "owner-menu-title"));
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
        if (slot == MINUS_SMALL_SLOT) {
            changePrice(-100);
            return;
        }
        if (slot == MINUS_BIG_SLOT) {
            changePrice(-1000);
            return;
        }
        if (slot == PLUS_SMALL_SLOT) {
            changePrice(100);
            return;
        }
        if (slot == PLUS_BIG_SLOT) {
            changePrice(1000);
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
        String livePrice = listing == null ? "?" : auctionService.formatPrice(listing.price(), listing.auctionId(), viewerId);
        String formattedEdited = auctionService.formatPrice(editedPrice, auctionId, viewerId);
        Map<String, String> pricePlaceholders = Map.of("price", formattedEdited);
        inventory.setItem(MINUS_SMALL_SLOT, actionItem(
                Material.RED_DYE,
                messageService.component(viewerId, "sell-price-minus-small", Map.of("step", auctionService.formatPrice(100, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(MINUS_BIG_SLOT, actionItem(
                Material.REDSTONE,
                messageService.component(viewerId, "sell-price-minus-big", Map.of("step", auctionService.formatPrice(1000, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(PLUS_SMALL_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component(viewerId, "sell-price-plus-small", Map.of("step", auctionService.formatPrice(100, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(PLUS_BIG_SLOT, actionItem(
                Material.EMERALD,
                messageService.component(viewerId, "sell-price-plus-big", Map.of("step", auctionService.formatPrice(1000, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(PRICE_SLOT, actionItem(
                Material.NAME_TAG,
                messageService.component(viewerId, "sell-price-current", pricePlaceholders)
        ));
        inventory.setItem(APPLY_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component(viewerId, "owner-price-apply"),
                messageService.components(viewerId, "owner-price-lore", Map.of(
                        "edited", formattedEdited,
                        "current", livePrice
                ))
        ));
        inventory.setItem(REMOVE_SLOT, actionItem(Material.RED_WOOL, messageService.component(viewerId, "owner-remove-listing")));
        inventory.setItem(BACK_SLOT, actionItem(Material.LIGHT_GRAY_DYE, messageService.component(viewerId, "owner-back")));
        if (listing != null) {
            ItemStack display = ItemStackCodec.decode(listing.itemBase64());
            if (display != null && !display.isEmpty()) {
                ListingItemPresentation.applyAuctionGuiName(display);
                inventory.setItem(ITEM_SLOT, display);
            }
        }
        fillDecor();
    }

    private void changePrice(int delta) {
        Player player = Bukkit.getPlayer(viewerId);
        int max = player == null ? auctionService.globalMaxPrice() : auctionService.maxPrice(player, auctionId);
        int next = editedPrice + delta;
        if (next < 1) {
            next = 1;
        }
        if (next > max) {
            next = max;
        }
        editedPrice = next;
        refresh();
    }

    private void fillDecor() {
        ItemStack decor = actionItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == ITEM_SLOT || slot == PRICE_SLOT || slot == MINUS_SMALL_SLOT || slot == MINUS_BIG_SLOT
                    || slot == PLUS_SMALL_SLOT || slot == PLUS_BIG_SLOT || slot == REMOVE_SLOT || slot == BACK_SLOT
                    || slot == APPLY_SLOT) {
                continue;
            }
            inventory.setItem(slot, decor);
        }
    }

    private ItemStack actionItem(Material material, Component title) {
        return actionItem(material, title, null);
    }

    private ItemStack actionItem(Material material, Component title, List<Component> lore) {
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
