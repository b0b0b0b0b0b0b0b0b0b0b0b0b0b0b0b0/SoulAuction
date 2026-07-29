package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AuctionSellMenu implements InventoryHolder {

    private static final int ITEM_SLOT = 22;
    private static final int PRICE_SLOT = 31;
    private static final int MINUS_SMALL_SLOT = 28;
    private static final int MINUS_BIG_SLOT = 29;
    private static final int PLUS_SMALL_SLOT = 33;
    private static final int PLUS_BIG_SLOT = 34;
    private static final int BACK_SLOT = 45;
    private static final int CONFIRM_SLOT = 53;

    private final UUID viewerId;
    private final String auctionId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final Inventory inventory;
    private int price;

    public AuctionSellMenu(UUID viewerId, String auctionId, AuctionService auctionService, MessageService messageService) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component("sell-menu-title", Map.of("auction", auctionService.auctionDisplayName(auctionId))));
        this.price = 100;
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

    public int itemSlot() {
        return ITEM_SLOT;
    }

    public ItemStack reservedItem() {
        return inventory.getItem(ITEM_SLOT);
    }

    public ItemStack takeReservedItem() {
        ItemStack item = inventory.getItem(ITEM_SLOT);
        inventory.setItem(ITEM_SLOT, null);
        return item;
    }

    public void putReservedItem(ItemStack item) {
        inventory.setItem(ITEM_SLOT, item);
    }

    public MenuAction clickTop(int slot) {
        if (slot == BACK_SLOT) {
            return MenuAction.BACK;
        }
        if (slot == CONFIRM_SLOT) {
            return MenuAction.CONFIRM;
        }
        if (slot == MINUS_SMALL_SLOT) {
            changePrice(-100);
            return MenuAction.REFRESH;
        }
        if (slot == MINUS_BIG_SLOT) {
            changePrice(-1000);
            return MenuAction.REFRESH;
        }
        if (slot == PLUS_SMALL_SLOT) {
            changePrice(100);
            return MenuAction.REFRESH;
        }
        if (slot == PLUS_BIG_SLOT) {
            changePrice(1000);
            return MenuAction.REFRESH;
        }
        if (slot == ITEM_SLOT) {
            return MenuAction.ITEM_SLOT;
        }
        return MenuAction.IGNORE;
    }

    public void refresh() {
        fillDecor();
        Map<String, String> placeholders = Map.of("price", auctionService.formatPrice(price, auctionService.economyType(auctionId)));
        inventory.setItem(MINUS_SMALL_SLOT, actionItem(
                Material.RED_DYE,
                messageService.component("sell-price-minus-small"),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(MINUS_BIG_SLOT, actionItem(
                Material.REDSTONE,
                messageService.component("sell-price-minus-big"),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(PLUS_SMALL_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component("sell-price-plus-small"),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(PLUS_BIG_SLOT, actionItem(
                Material.EMERALD,
                messageService.component("sell-price-plus-big"),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(BACK_SLOT, actionItem(Material.GRAY_DYE, messageService.component("sell-button-back")));
        inventory.setItem(CONFIRM_SLOT, actionItem(Material.GREEN_WOOL, messageService.component("sell-button-confirm")));
        inventory.setItem(PRICE_SLOT, actionItem(Material.NAME_TAG, messageService.component(
                "sell-price-current",
                Map.of("price", auctionService.formatPrice(price, auctionService.economyType(auctionId)))
        )));
    }

    public int price() {
        return price;
    }

    private void fillDecor() {
        ItemStack decor = actionItem(Material.PURPLE_WOOL, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == ITEM_SLOT || slot == PRICE_SLOT || slot == MINUS_SMALL_SLOT || slot == MINUS_BIG_SLOT
                    || slot == PLUS_SMALL_SLOT || slot == PLUS_BIG_SLOT || slot == BACK_SLOT || slot == CONFIRM_SLOT) {
                continue;
            }
            inventory.setItem(slot, decor);
        }
    }

    private ItemStack actionItem(Material material, Component title) {
        return actionItem(material, title, null);
    }

    private ItemStack actionItem(Material material, Component title, java.util.List<Component> lore) {
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

    private void changePrice(int delta) {
        int max = Math.max(1, auctionService.maxPrice());
        int next = price + delta;
        if (next < 1) {
            next = 1;
        }
        if (next > max) {
            next = max;
        }
        price = next;
        refresh();
    }

    public enum MenuAction {
        BACK,
        CONFIRM,
        ITEM_SLOT,
        REFRESH,
        IGNORE
    }
}
