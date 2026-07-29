package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.PlayerHistoryView;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class PlayerHubMenu implements InventoryHolder {

    private static final int SELLING_SLOT = 20;
    private static final int EXPIRED_SLOT = 22;
    private static final int PURCHASED_SLOT = 24;
    private static final int MY_SALES_SLOT = 30;
    private static final int RECENT_SLOT = 32;
    private static final int BACK_SLOT = 49;

    private final UUID viewerId;
    private final String auctionId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final Inventory inventory;

    public PlayerHubMenu(
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component("hub-title"));
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

    public PlayerHistoryView viewAt(int slot) {
        if (slot == SELLING_SLOT) {
            return PlayerHistoryView.SELLING;
        }
        if (slot == EXPIRED_SLOT) {
            return PlayerHistoryView.EXPIRED;
        }
        if (slot == PURCHASED_SLOT) {
            return PlayerHistoryView.PURCHASED;
        }
        if (slot == MY_SALES_SLOT) {
            return PlayerHistoryView.MY_SALES;
        }
        if (slot == RECENT_SLOT) {
            return PlayerHistoryView.RECENT_AUCTION;
        }
        return null;
    }

    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    private void refresh() {
        inventory.clear();
        inventory.setItem(SELLING_SLOT, button(Material.LIME_DYE, "hub-selling"));
        inventory.setItem(EXPIRED_SLOT, button(Material.ORANGE_DYE, "hub-expired"));
        inventory.setItem(PURCHASED_SLOT, button(Material.DIAMOND, "hub-purchased"));
        inventory.setItem(MY_SALES_SLOT, button(Material.GOLD_INGOT, "hub-my-sales"));
        inventory.setItem(RECENT_SLOT, button(Material.BOOK, "hub-recent-sales"));
        inventory.setItem(BACK_SLOT, button(Material.GRAY_DYE, "hub-back"));
        ItemStack decor = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta decorMeta = decor.getItemMeta();
        if (decorMeta != null) {
            decorMeta.displayName(net.kyori.adventure.text.Component.text(" "));
            decor.setItemMeta(decorMeta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, decor);
            }
        }
    }

    private ItemStack button(Material material, String messageKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messageService.component(messageKey));
            item.setItemMeta(meta);
        }
        return item;
    }
}
