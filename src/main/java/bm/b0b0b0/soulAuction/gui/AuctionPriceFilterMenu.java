package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowseFilterState;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AuctionPriceFilterMenu implements InventoryHolder {

    private static final int MIN_MINUS = 10;
    private static final int MIN_PLUS = 11;
    private static final int MAX_MINUS = 12;
    private static final int MAX_PLUS = 13;
    private static final int APPLY = 22;
    private static final int CLEAR = 24;
    private static final int BACK = 49;

    private final UUID viewerId;
    private final String auctionId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final GuiGeneralSettings guiSettings;
    private final Inventory inventory;
    private int draftMin;
    private int draftMax;

    public AuctionPriceFilterMenu(
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService,
            GuiGeneralSettings guiSettings
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.guiSettings = guiSettings;
        BrowseFilterState state = auctionService.browseFilterState(viewerId);
        this.draftMin = state.minPrice();
        this.draftMax = state.maxPrice();
        this.inventory = Bukkit.createInventory(this, 54, messageService.component("price-filter-title"));
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

    public void click(int slot) {
        if (slot == MIN_MINUS) {
            draftMin = Math.max(0, draftMin - 100);
        } else if (slot == MIN_PLUS) {
            draftMin = draftMin + 100;
        } else if (slot == MAX_MINUS) {
            draftMax = Math.max(0, draftMax - 100);
        } else if (slot == MAX_PLUS) {
            draftMax = draftMax + 100;
        } else if (slot == CLEAR) {
            draftMin = 0;
            draftMax = 0;
        } else if (slot == APPLY) {
            BrowseFilterState current = auctionService.browseFilterState(viewerId);
            auctionService.setBrowseFilterState(viewerId, new BrowseFilterState(
                    current.searchQuery(),
                    current.favoritesOnly(),
                    current.favoriteListingsOnly(),
                    draftMin,
                    draftMax,
                    current.sellerFilter()
            ));
            openBrowser();
            return;
        } else if (slot == BACK) {
            openBrowser();
            return;
        } else {
            return;
        }
        refresh();
    }

    private void openBrowser() {
        org.bukkit.entity.Player player = Bukkit.getPlayer(viewerId);
        if (player == null) {
            return;
        }
        AuctionBrowserMenu menu = new AuctionBrowserMenu(
                viewerId,
                auctionId,
                auctionService,
                messageService,
                guiSettings,
                0,
                auctionService.browseFilterState(viewerId).searchQuery()
        );
        player.openInventory(menu.getInventory());
    }

    private void refresh() {
        inventory.clear();
        String step = auctionService.formatPrice(100, auctionId, viewerId);
        String minFormatted = auctionService.formatPrice(draftMin, auctionId, viewerId);
        String maxFormatted = draftMax <= 0 ? "-" : auctionService.formatPrice(draftMax, auctionId, viewerId);
        inventory.setItem(MIN_MINUS, labeled(Material.RED_DYE, "price-filter-min-minus", Map.of("step", step)));
        inventory.setItem(MIN_PLUS, labeled(Material.LIME_DYE, "price-filter-min-plus", Map.of("step", step)));
        inventory.setItem(MAX_MINUS, labeled(Material.RED_DYE, "price-filter-max-minus", Map.of("step", step)));
        inventory.setItem(MAX_PLUS, labeled(Material.LIME_DYE, "price-filter-max-plus", Map.of("step", step)));
        inventory.setItem(APPLY, labeled(Material.EMERALD, "price-filter-apply"));
        inventory.setItem(CLEAR, labeled(Material.BARRIER, "price-filter-clear"));
        inventory.setItem(BACK, labeled(Material.ARROW, "price-filter-back"));
        inventory.setItem(4, labeled(Material.GOLD_INGOT, "price-filter-info", Map.of(
                "min", minFormatted,
                "max", maxFormatted
        )));
    }

    private ItemStack labeled(Material material, String messageKey) {
        return labeled(material, messageKey, Map.of());
    }

    private ItemStack labeled(Material material, String messageKey, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(messageService.component(messageKey, placeholders));
            item.setItemMeta(meta);
        }
        return item;
    }
}
