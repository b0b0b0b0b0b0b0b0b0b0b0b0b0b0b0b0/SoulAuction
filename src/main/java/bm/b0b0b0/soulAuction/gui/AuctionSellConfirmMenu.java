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
import bm.b0b0b0.soulAuction.util.ListingItemPresentation;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AuctionSellConfirmMenu implements InventoryHolder {

    private static final int NO_SLOT = 20;
    private static final int ITEM_SLOT = 22;
    private static final int YES_SLOT = 24;
    private static final int SUMMARY_SLOT = 31;
    private static final int EXIT_SLOT = 49;

    private final UUID viewerId;
    private final String auctionId;
    private final int price;
    private final int sellAmount;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final Inventory inventory;
    private ItemStack heldStack;
    private volatile boolean skipCloseReturn;
    private volatile boolean heldReleased;

    public AuctionSellConfirmMenu(
            UUID viewerId,
            String auctionId,
            ItemStack heldStack,
            int price,
            int sellAmount,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.price = price;
        this.sellAmount = Math.max(1, sellAmount);
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.heldStack = heldStack == null ? null : heldStack.clone();
        this.inventory = Bukkit.createInventory(
                this,
                54,
                messageService.component(viewerId, "sell-confirm-title", Map.of("auction", auctionService.auctionDisplayName(auctionId)))
        );
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

    public int price() {
        return price;
    }

    public int sellAmount() {
        return sellAmount;
    }

    public void skipCloseReturn() {
        this.skipCloseReturn = true;
    }

    public boolean shouldReturnOnClose() {
        return !skipCloseReturn && !heldReleased;
    }

    public boolean isHeldReleased() {
        return heldReleased;
    }

    public ItemStack takeHeldStack() {
        if (heldReleased) {
            return null;
        }
        heldReleased = true;
        skipCloseReturn = true;
        ItemStack taken = heldStack;
        heldStack = null;
        inventory.setItem(ITEM_SLOT, null);
        if (taken == null || taken.isEmpty()) {
            return null;
        }
        return taken.clone();
    }

    public ItemStack heldStack() {
        return heldStack == null ? null : heldStack.clone();
    }

    public boolean isYes(int slot) {
        return slot == YES_SLOT;
    }

    public boolean isNo(int slot) {
        return slot == NO_SLOT;
    }

    public boolean isExit(int slot) {
        return slot == EXIT_SLOT;
    }

    public void refresh() {
        fillDecor();
        if (heldStack != null && !heldStack.isEmpty()) {
            ItemStack preview = heldStack.clone();
            preview.setAmount(Math.min(sellAmount, preview.getAmount()));
            ListingItemPresentation.applyAuctionGuiName(preview);
            inventory.setItem(ITEM_SLOT, preview);
        }
        String formattedPrice = auctionService.formatPrice(price, auctionId, viewerId);
        int inCell = heldStack == null ? 0 : heldStack.getAmount();
        inventory.setItem(SUMMARY_SLOT, button(
                Material.BOOK,
                messageService.component(viewerId, "sell-confirm-summary-title"),
                messageService.components(viewerId, "sell-confirm-summary-lore", Map.of(
                        "price", formattedPrice,
                        "amount", String.valueOf(sellAmount),
                        "in_cell", String.valueOf(inCell),
                        "auction", auctionService.auctionDisplayName(auctionId)
                ))
        ));
        inventory.setItem(YES_SLOT, button(Material.LIME_DYE, messageService.component(viewerId, "sell-confirm-yes"), null));
        inventory.setItem(NO_SLOT, button(Material.RED_DYE, messageService.component(viewerId, "sell-confirm-no"), null));
        inventory.setItem(EXIT_SLOT, button(Material.LIGHT_GRAY_DYE, messageService.component(viewerId, "sell-confirm-exit"), null));
    }

    private void fillDecor() {
        ItemStack decor = button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == NO_SLOT || slot == ITEM_SLOT || slot == YES_SLOT || slot == SUMMARY_SLOT || slot == EXIT_SLOT) {
                continue;
            }
            inventory.setItem(slot, decor);
        }
    }

    private ItemStack button(Material material, Component title, java.util.List<Component> lore) {
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
