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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component("history-title"));
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
            String sellerName = offlineName(sale.sellerId());
            String buyerName = sale.buyerId() == null ? "-" : offlineName(sale.buyerId());
            String price = auctionService.formatPrice(sale.price(), sale.economyType());
            String tax = String.valueOf(sale.tax());
            String time = TIME_FORMAT.format(Instant.ofEpochMilli(sale.createdAtEpochMillis()).atZone(ZoneId.systemDefault()));
            inventory.setItem(i, historyItem(
                    messageService.component("history-item-title", Map.of("id", String.valueOf(sale.historyId()))),
                    messageService.components("history-item-lore", Map.of(
                            "seller", sellerName,
                            "buyer", buyerName,
                            "price", price,
                            "tax", tax,
                            "time", time
                    ))
            ));
        }
        inventory.setItem(BACK_SLOT, historyItem(messageService.component("history-back"), null));
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

    private String offlineName(UUID uuid) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        return uuid.toString();
    }
}
