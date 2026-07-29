package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class FavoriteSellersMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int EMPTY_SLOT = 22;

    private final UUID viewerId;
    private final String auctionId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final GuiGeneralSettings guiSettings;
    private final Inventory inventory;
    private final Map<Integer, UUID> sellerBySlot = new HashMap<>();
    private int page;

    public FavoriteSellersMenu(
            UUID viewerId,
            String auctionId,
            int page,
            AuctionService auctionService,
            MessageService messageService,
            GuiGeneralSettings guiSettings
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.page = Math.max(0, page);
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.guiSettings = guiSettings;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component(viewerId, "favorite-sellers-title"));
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

    public int page() {
        return page;
    }

    public UUID sellerIdAt(int slot) {
        return sellerBySlot.get(slot);
    }

    public boolean isPrev(int slot) {
        return slot == PREV_SLOT;
    }

    public boolean isNext(int slot) {
        return slot == NEXT_SLOT;
    }

    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            refresh();
        }
    }

    public void nextPage() {
        List<UUID> all = auctionService.favoriteSellers(viewerId);
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (page < maxPage) {
            page++;
            refresh();
        }
    }

    public void refresh() {
        inventory.clear();
        sellerBySlot.clear();
        List<UUID> all = auctionService.favoriteSellers(viewerId);
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());
        if (from >= to) {
            inventory.setItem(EMPTY_SLOT, paper(
                    messageService.component(viewerId, "favorite-sellers-empty-title"),
                    messageService.components(viewerId, "favorite-sellers-empty-lore")
            ));
        } else {
            for (int i = from; i < to; i++) {
                UUID sellerId = all.get(i);
                int slot = i - from;
                sellerBySlot.put(slot, sellerId);
                inventory.setItem(slot, sellerHead(sellerId));
            }
        }
        if (page > 0) {
            inventory.setItem(PREV_SLOT, navItem(
                    guiSettings.previousPageMaterial,
                    "button-prev-page",
                    "favorite-sellers-prev-lore"
            ));
        }
        if (page < maxPage) {
            inventory.setItem(NEXT_SLOT, navItem(
                    guiSettings.nextPageMaterial,
                    "button-next-page",
                    "favorite-sellers-next-lore"
            ));
        }
        Material backMaterial = Material.matchMaterial(guiSettings.backButtonMaterial);
        if (backMaterial == null) {
            backMaterial = Material.LIGHT_GRAY_DYE;
        }
        inventory.setItem(BACK_SLOT, actionItem(
                backMaterial,
                messageService.component(viewerId, "favorite-sellers-back"),
                messageService.components(viewerId, "favorite-sellers-back-lore")
        ));
        fillDecor();
    }

    private ItemStack sellerHead(UUID sellerId) {
        String name = offlineName(sellerId);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(sellerId);
            skullMeta.setOwningPlayer(offline);
            skullMeta.displayName(messageService.component(viewerId, "favorite-sellers-entry-title", Map.of("seller", name)));
            skullMeta.lore(messageService.components(viewerId, "favorite-sellers-entry-lore", Map.of("seller", name)));
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack navItem(String materialName, String titleKey, String loreKey) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.PAPER;
        }
        return actionItem(
                material,
                messageService.component(viewerId, titleKey),
                messageService.components(viewerId, loreKey)
        );
    }

    private ItemStack paper(Component title, List<Component> lore) {
        return actionItem(Material.PAPER, title, lore);
    }

    private ItemStack actionItem(Material material, Component title, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(title);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillDecor() {
        ItemStack decor = actionItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) != null) {
                continue;
            }
            inventory.setItem(slot, decor);
        }
    }

    private String offlineName(UUID uuid) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        return uuid.toString();
    }
}
