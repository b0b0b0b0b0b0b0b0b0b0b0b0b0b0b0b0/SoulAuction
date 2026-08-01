package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import bm.b0b0b0.soulAuction.util.PlayerDisplayNames;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RecentSalesMenu implements InventoryHolder {

    private static final int BACK_SLOT = 49;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final UUID viewerId;
    private final String auctionId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final Inventory inventory;

    public RecentSalesMenu(
            Player viewer,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this(viewer.getUniqueId(), auctionId, auctionService, messageService, messageService.component(viewer, "history-title"));
    }

    public RecentSalesMenu(
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this(viewerId, auctionId, auctionService, messageService, messageService.component(viewerId, "history-title"));
    }

    private RecentSalesMenu(
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService,
            net.kyori.adventure.text.Component inventoryTitle
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, inventoryTitle);
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

    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    public void refresh() {
        inventory.clear();
        List<DealHistoryEntry> sales = auctionService.recentSales(auctionId, 45);
        for (int i = 0; i < sales.size() && i < 45; i++) {
            DealHistoryEntry sale = sales.get(i);
            String sellerName = PlayerDisplayNames.resolve(sale.sellerId(), sale.sellerName());
            String buyerName = PlayerDisplayNames.resolve(sale.buyerId(), sale.buyerName());
            String price = auctionService.formatPrice(sale.price(), sale.auctionId(), viewerId);
            String tax = auctionService.formatPrice(sale.tax(), sale.auctionId(), viewerId);
            String time = TIME_FORMAT.format(Instant.ofEpochMilli(sale.createdAtEpochMillis()).atZone(ZoneId.systemDefault()));
            inventory.setItem(i, historyItem(
                    messageService.component(viewerId, "history-item-title", Map.of("id", String.valueOf(sale.historyId()))),
                    messageService.components(viewerId, "history-item-lore", Map.of(
                            "seller", sellerName,
                            "buyer", buyerName,
                            "price", price,
                            "tax", tax,
                            "time", time
                    ))
            ));
        }
        inventory.setItem(BACK_SLOT, backButton(
                messageService.component(viewerId, "hub-submenu-back"),
                messageService.components(viewerId, "hub-submenu-back-lore")
        ));
    }

    private ItemStack backButton(net.kyori.adventure.text.Component title, List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_DYE);
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            itemMeta.displayName(title);
            if (lore != null && !lore.isEmpty()) {
                itemMeta.lore(lore);
            }
            item.setItemMeta(itemMeta);
        }
        return item;
    }

    private ItemStack historyItem(net.kyori.adventure.text.Component title, List<net.kyori.adventure.text.Component> lore) {
        ItemStack item = new ItemStack(Material.PAPER);
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
