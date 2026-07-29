package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
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

public final class AuctionSellMenu implements InventoryHolder {

    private static final int ITEM_SLOT = 22;
    private static final int PRICE_SLOT = 31;
    private static final int MINUS_SMALL_SLOT = 28;
    private static final int MINUS_BIG_SLOT = 29;
    private static final int QTY_MINUS_SLOT = 20;
    private static final int QTY_PLUS_SLOT = 24;
    private static final int AMOUNT_INFO_SLOT = 40;
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
    private int sellAmount;
    private ItemStack backingStack;
    private volatile boolean skipCloseReturn;
    private volatile boolean reservedReleased;

    public AuctionSellMenu(UUID viewerId, String auctionId, AuctionService auctionService, MessageService messageService) {
        this(viewerId, auctionId, auctionService, messageService, 100, 0);
    }

    public AuctionSellMenu(
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService,
            int initialPrice,
            int initialSellAmount
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component("sell-menu-title", Map.of("auction", auctionService.auctionDisplayName(auctionId))));
        this.price = Math.max(1, initialPrice);
        this.sellAmount = Math.max(0, initialSellAmount);
        refresh();
    }

    public void syncAmountFromItem() {
        reconcileBackingFromSlot();
        clampSellAmount();
        refresh();
    }

    private void reconcileBackingFromSlot() {
        ItemStack inSlot = inventory.getItem(ITEM_SLOT);
        if (inSlot == null || inSlot.isEmpty()) {
            backingStack = null;
            if (sellAmount < 1) {
                sellAmount = 1;
            }
            return;
        }
        if (backingStack == null || !backingStack.isSimilar(inSlot)) {
            backingStack = inSlot.clone();
            sellAmount = backingStack.getAmount();
            return;
        }
        int inAmount = inSlot.getAmount();
        int reservedAmount = backingStack.getAmount();
        if (inAmount > reservedAmount) {
            backingStack = inSlot.clone();
            sellAmount = inAmount;
            return;
        }
        if (inAmount < sellAmount) {
            sellAmount = inAmount;
        }
    }

    public boolean hasBackingStack() {
        return backingStack != null && !backingStack.isEmpty();
    }

    public void skipCloseReturn() {
        this.skipCloseReturn = true;
    }

    public boolean shouldReturnOnClose() {
        return !skipCloseReturn && !reservedReleased;
    }

    public boolean isReservedReleased() {
        return reservedReleased;
    }

    public int sellAmount() {
        return sellAmount;
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
        if (backingStack == null || backingStack.isEmpty()) {
            return inventory.getItem(ITEM_SLOT);
        }
        return backingStack.clone();
    }

    public ItemStack takeReservedItem() {
        if (reservedReleased) {
            return null;
        }
        reconcileBackingFromSlot();
        ItemStack taken = backingStack;
        backingStack = null;
        reservedReleased = true;
        skipCloseReturn = true;
        inventory.setItem(ITEM_SLOT, null);
        if (taken == null || taken.isEmpty()) {
            return null;
        }
        return taken.clone();
    }

    public void putReservedItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            backingStack = null;
            sellAmount = 1;
        } else {
            backingStack = item.clone();
            sellAmount = backingStack.getAmount();
        }
        refresh();
    }

    public MenuAction clickTop(int slot) {
        if (slot == BACK_SLOT) {
            return MenuAction.BACK;
        }
        if (slot == CONFIRM_SLOT) {
            return MenuAction.TO_CONFIRM;
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
        if (slot == QTY_MINUS_SLOT) {
            changeAmount(-1);
            return MenuAction.REFRESH;
        }
        if (slot == QTY_PLUS_SLOT) {
            changeAmount(1);
            return MenuAction.REFRESH;
        }
        if (slot == ITEM_SLOT) {
            return MenuAction.ITEM_SLOT;
        }
        return MenuAction.IGNORE;
    }

    public void refresh() {
        reconcileBackingFromSlot();
        clampSellAmount();
        fillDecor();
        String formattedPrice = auctionService.formatPrice(price, auctionId, viewerId);
        Map<String, String> placeholders = Map.of("price", formattedPrice);
        inventory.setItem(MINUS_SMALL_SLOT, actionItem(
                Material.RED_DYE,
                messageService.component("sell-price-minus-small", Map.of("step", auctionService.formatPrice(100, auctionId, viewerId))),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(MINUS_BIG_SLOT, actionItem(
                Material.REDSTONE,
                messageService.component("sell-price-minus-big", Map.of("step", auctionService.formatPrice(1000, auctionId, viewerId))),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(PLUS_SMALL_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component("sell-price-plus-small", Map.of("step", auctionService.formatPrice(100, auctionId, viewerId))),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(PLUS_BIG_SLOT, actionItem(
                Material.EMERALD,
                messageService.component("sell-price-plus-big", Map.of("step", auctionService.formatPrice(1000, auctionId, viewerId))),
                messageService.components("sell-price-button-lore", placeholders)
        ));
        inventory.setItem(QTY_MINUS_SLOT, actionItem(
                Material.REDSTONE_BLOCK,
                messageService.component("sell-amount-minus", Map.of("step", "1")),
                messageService.components("sell-amount-minus-lore", amountPlaceholders())
        ));
        inventory.setItem(QTY_PLUS_SLOT, actionItem(
                Material.EMERALD_BLOCK,
                messageService.component("sell-amount-plus", Map.of("step", "1")),
                messageService.components("sell-amount-plus-lore", amountPlaceholders())
        ));
        inventory.setItem(AMOUNT_INFO_SLOT, actionItem(
                Material.CHEST,
                messageService.component("sell-amount-info-title", amountPlaceholders()),
                messageService.components("sell-amount-info-lore", amountPlaceholders())
        ));
        inventory.setItem(BACK_SLOT, actionItem(Material.LIGHT_GRAY_DYE, messageService.component("sell-button-back")));
        inventory.setItem(CONFIRM_SLOT, actionItem(Material.LIME_DYE, messageService.component("sell-button-next")));
        inventory.setItem(PRICE_SLOT, actionItem(Material.NAME_TAG, messageService.component(
                "sell-price-current",
                Map.of("price", formattedPrice)
        )));
        paintItemSlot();
    }

    public int price() {
        return price;
    }

    public void setSellAmount(int amount) {
        sellAmount = amount;
        clampSellAmount();
    }

    public void setPrice(int value) {
        price = Math.max(1, value);
    }

    private void paintItemSlot() {
        if (backingStack == null || backingStack.isEmpty()) {
            return;
        }
        ItemStack display = backingStack.clone();
        display.setAmount(Math.min(sellAmount, backingStack.getAmount()));
        inventory.setItem(ITEM_SLOT, display);
    }

    private void fillDecor() {
        ItemStack decor = actionItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == ITEM_SLOT || slot == PRICE_SLOT || slot == MINUS_SMALL_SLOT || slot == MINUS_BIG_SLOT
                    || slot == PLUS_SMALL_SLOT || slot == PLUS_BIG_SLOT || slot == QTY_MINUS_SLOT || slot == QTY_PLUS_SLOT
                    || slot == AMOUNT_INFO_SLOT || slot == BACK_SLOT || slot == CONFIRM_SLOT) {
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
        Player player = Bukkit.getPlayer(viewerId);
        int max = player == null ? auctionService.globalMaxPrice() : auctionService.maxPrice(player, auctionId);
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

    private void changeAmount(int delta) {
        reconcileBackingFromSlot();
        sellAmount += delta;
        clampSellAmount();
        refresh();
    }

    private void clampSellAmount() {
        int maxAmount = maxStackAmount();
        if (sellAmount < 1) {
            sellAmount = 1;
        }
        if (sellAmount > maxAmount) {
            sellAmount = maxAmount;
        }
    }

    private int maxStackAmount() {
        ItemStack inSlot = inventory.getItem(ITEM_SLOT);
        int inSlotAmount = inSlot == null || inSlot.isEmpty() ? 0 : inSlot.getAmount();
        int backingAmount = backingStack == null || backingStack.isEmpty() ? 0 : backingStack.getAmount();
        if (inSlotAmount <= 0 && backingAmount <= 0) {
            return 1;
        }
        if (inSlotAmount > 0 && backingAmount > 0 && !backingStack.isSimilar(inSlot)) {
            return inSlotAmount;
        }
        return Math.max(inSlotAmount, backingAmount);
    }

    private Map<String, String> amountPlaceholders() {
        return Map.of("amount", String.valueOf(sellAmount), "max", String.valueOf(maxStackAmount()));
    }

    public enum MenuAction {
        BACK,
        TO_CONFIRM,
        ITEM_SLOT,
        REFRESH,
        IGNORE
    }
}
