package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.PlayerHistoryView;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
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

public final class PlayerRecordsMenu implements InventoryHolder {

    private static final int BACK_SLOT = 49;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final UUID viewerId;
    private final UUID subjectId;
    private final String auctionId;
    private final PlayerHistoryView view;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final Inventory inventory;

    public PlayerRecordsMenu(
            UUID viewerId,
            String auctionId,
            PlayerHistoryView view,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.subjectId = viewerId;
        this.auctionId = auctionId;
        this.view = view;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component(titleKey(view)));
        refresh();
    }

    public PlayerRecordsMenu(
            UUID viewerId,
            UUID subjectId,
            String auctionId,
            PlayerHistoryView view,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.subjectId = subjectId == null ? viewerId : subjectId;
        this.auctionId = auctionId;
        this.view = view;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component(titleKey(view)));
        refresh();
    }

    public UUID subjectId() {
        return subjectId;
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

    public PlayerHistoryView view() {
        return view;
    }

    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    public void refresh() {
        inventory.clear();
        switch (view) {
            case SELLING -> fillSelling();
            case EXPIRED -> fillExpired();
            case PURCHASED -> fillPurchased();
            case MY_SALES -> fillMySales();
            case RECENT_AUCTION -> fillRecentAuction();
        }
        inventory.setItem(BACK_SLOT, paper(messageService.component("hub-back"), null));
    }

    private void fillSelling() {
        List<AuctionListing> listings = auctionService.myListings(subjectId, auctionId);
        for (int i = 0; i < listings.size() && i < 45; i++) {
            AuctionListing listing = listings.get(i);
            ItemStack item = ItemStackCodec.decode(listing.itemBase64());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.lore(messageService.components("record-selling-lore", Map.of(
                        "id", String.valueOf(listing.listingId()),
                        "price", auctionService.formatPrice(listing.price(), listing.economyType())
                )));
                item.setItemMeta(meta);
            }
            inventory.setItem(i, item);
        }
    }

    private void fillExpired() {
        List<ClaimEntry> claims = auctionService.expiredClaims(subjectId);
        int slotIndex = 0;
        for (ClaimEntry claim : claims) {
            if (slotIndex >= 45) {
                break;
            }
            if (auctionId != null && !auctionId.isBlank() && !claim.auctionId().equalsIgnoreCase(auctionId)) {
                continue;
            }
            ItemStack item = ItemStackCodec.decode(claim.itemBase64());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.lore(messageService.components("record-expired-lore", Map.of(
                        "id", String.valueOf(claim.claimId()),
                        "reason", claim.reason(),
                        "auction", claim.auctionId()
                )));
                item.setItemMeta(meta);
            }
            inventory.setItem(slotIndex++, item);
        }
    }

    private void fillPurchased() {
        List<DealHistoryEntry> entries = auctionService.playerPurchases(subjectId, auctionId, 45);
        for (int i = 0; i < entries.size(); i++) {
            inventory.setItem(i, dealPaper(entries.get(i), true));
        }
    }

    private void fillMySales() {
        List<DealHistoryEntry> entries = auctionService.playerSalesHistory(subjectId, auctionId, 45);
        for (int i = 0; i < entries.size(); i++) {
            inventory.setItem(i, dealPaper(entries.get(i), false));
        }
    }

    private void fillRecentAuction() {
        List<DealHistoryEntry> entries = auctionService.recentSales(auctionId, 45);
        for (int i = 0; i < entries.size(); i++) {
            inventory.setItem(i, dealPaper(entries.get(i), false));
        }
    }

    private ItemStack dealPaper(DealHistoryEntry entry, boolean asPurchase) {
        String sellerName = offlineName(entry.sellerId());
        String buyerName = entry.buyerId() == null ? "-" : offlineName(entry.buyerId());
        String price = auctionService.formatPrice(entry.price(), entry.economyType());
        String time = TIME_FORMAT.format(Instant.ofEpochMilli(entry.createdAtEpochMillis()).atZone(ZoneId.systemDefault()));
        String total = auctionService.formatPrice(entry.price() + entry.buyTax(), entry.economyType());
        return paper(
                messageService.component("history-item-title", Map.of("id", String.valueOf(entry.historyId()))),
                messageService.components(asPurchase ? "record-purchased-lore" : "history-item-lore", Map.of(
                        "seller", sellerName,
                        "buyer", buyerName,
                        "price", price,
                        "tax", String.valueOf(entry.tax()),
                        "buytax", String.valueOf(entry.buyTax()),
                        "total", total,
                        "time", time
                ))
        );
    }

    private ItemStack paper(net.kyori.adventure.text.Component title, List<net.kyori.adventure.text.Component> lore) {
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

    private static String titleKey(PlayerHistoryView view) {
        return switch (view) {
            case SELLING -> "record-selling-title";
            case EXPIRED -> "record-expired-title";
            case PURCHASED -> "record-purchased-title";
            case MY_SALES -> "record-my-sales-title";
            case RECENT_AUCTION -> "history-title";
        };
    }
}
