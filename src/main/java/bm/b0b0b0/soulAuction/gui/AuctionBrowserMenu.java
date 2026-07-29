package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AuctionBrowserMenu implements InventoryHolder {

    private final UUID viewerId;
    private final String auctionId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final GuiGeneralSettings guiSettings;
    private final Inventory inventory;
    private final Map<Integer, Long> listingBySlot;
    private final Map<AuctionCategory, String> categoryNameByType;
    private int page;
    private AuctionSort sort;
    private AuctionCategory category;
    private String searchQuery;

    public AuctionBrowserMenu(
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService,
            GuiGeneralSettings guiSettings
    ) {
        this(viewerId, auctionId, auctionService, messageService, guiSettings, 0, null);
    }

    public AuctionBrowserMenu(
            UUID viewerId,
            String auctionId,
            AuctionService auctionService,
            MessageService messageService,
            GuiGeneralSettings guiSettings,
            int initialPage,
            String searchQuery
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.guiSettings = guiSettings;
        this.inventory = Bukkit.createInventory(
                this,
                guiSettings.size,
                messageService.component("auction-title", Map.of("auction", auctionService.auctionDisplayName(auctionId)))
        );
        this.listingBySlot = new HashMap<>();
        this.categoryNameByType = createCategoryNames(messageService);
        this.page = Math.max(0, initialPage);
        this.sort = AuctionSort.NEWEST;
        this.category = AuctionCategory.ALL;
        this.searchQuery = searchQuery == null || searchQuery.isBlank() ? null : searchQuery.trim();
        refresh();
    }

    public String searchQuery() {
        return searchQuery;
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
        if (slot == guiSettings.previousPageSlot) {
            previousPage();
            return;
        }
        if (slot == guiSettings.historySlot) {
            return;
        }
        if (slot == guiSettings.nextPageSlot) {
            nextPage();
            return;
        }
        if (slot == guiSettings.sortSlot) {
            sort = sort.next();
            refresh();
            return;
        }
        if (slot == guiSettings.categorySlot) {
            category = nextCategory(category);
            page = 0;
            refresh();
            return;
        }
        if (slot == guiSettings.refreshSlot) {
            refresh();
            return;
        }
        Long listingId = listingBySlot.get(slot);
        if (listingId == null) {
            return;
        }
        if (!(inventory.getHolder(false) instanceof AuctionBrowserMenu)) {
            return;
        }
        refresh();
    }

    public Long listingIdAt(int slot) {
        return listingBySlot.get(slot);
    }

    public void refresh() {
        listingBySlot.clear();
        inventory.clear();
        List<Integer> listingSlots = guiSettings.listingSlots;
        int visible = listingSlots.size();
        AuctionService.BrowsePage browsePage = auctionService.browsePage(auctionId, sort, category, page, visible, searchQuery);
        int total = browsePage.total();
        int maxPage = Math.max(0, (total - 1) / visible);
        List<AuctionListing> listings = browsePage.listings();
        for (int i = 0; i < listings.size() && i < listingSlots.size(); i++) {
            AuctionListing listing = listings.get(i);
            int slot = listingSlots.get(i);
            ItemStack item = ItemStackCodec.decode(listing.itemBase64());
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null) {
                itemMeta.lore(messageService.components(
                        "listing-lore",
                        Map.of("seller", listing.sellerName(), "price", auctionService.formatPrice(listing.price(), listing.economyType()))
                ));
                item.setItemMeta(itemMeta);
            }
            inventory.setItem(slot, item);
            listingBySlot.put(slot, listing.listingId());
        }
        if (page > 0) {
            inventory.setItem(
                    guiSettings.previousPageSlot,
                    actionItem(guiSettings.previousPageMaterial, guiSettings.previousPageCustomModelData, messageService.component("button-prev-page"))
            );
        }
        if (page < maxPage) {
            inventory.setItem(
                    guiSettings.nextPageSlot,
                    actionItem(guiSettings.nextPageMaterial, guiSettings.nextPageCustomModelData, messageService.component("button-next-page"))
            );
        }
        inventory.setItem(
                guiSettings.historySlot,
                actionItem(guiSettings.historyMaterial, guiSettings.historyCustomModelData, messageService.component("button-history"))
        );
        inventory.setItem(
                guiSettings.refreshSlot,
                actionItem(
                        guiSettings.refreshMaterial,
                        guiSettings.refreshCustomModelData,
                        searchQuery == null
                                ? messageService.component("button-refresh")
                                : messageService.component("button-refresh-search", Map.of("query", searchQuery))
                )
        );
        inventory.setItem(
                guiSettings.sortSlot,
                actionItem(
                        guiSettings.sortMaterial,
                        guiSettings.sortCustomModelData,
                        messageService.component("button-sort", Map.of("sort", sortName(sort)))
                )
        );
        inventory.setItem(
                guiSettings.categorySlot,
                actionItem(
                        guiSettings.categoryMaterial,
                        guiSettings.categoryCustomModelData,
                        messageService.component("button-category", Map.of("category", categoryName(category)))
                )
        );
    }

    public void nextPage() {
        int visible = guiSettings.listingSlots.size();
        int total = auctionService.count(auctionId, category, searchQuery);
        int maxPage = Math.max(0, (total - 1) / visible);
        if (page < maxPage) {
            page++;
            refresh();
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            refresh();
        }
    }

    private ItemStack actionItem(String materialName, int customModelData, Component title) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            itemMeta.displayName(title);
            if (customModelData >= 0) {
                itemMeta.setCustomModelData(customModelData);
            }
            item.setItemMeta(itemMeta);
        }
        return item;
    }

    private AuctionCategory nextCategory(AuctionCategory from) {
        AuctionCategory[] values = AuctionCategory.values();
        int nextOrdinal = from.ordinal() + 1;
        if (nextOrdinal >= values.length) {
            return AuctionCategory.ALL;
        }
        return values[nextOrdinal];
    }

    private String sortName(AuctionSort value) {
        return switch (value) {
            case NEWEST -> messageService.raw("sort-newest");
            case OLDEST -> messageService.raw("sort-oldest");
            case PRICE_ASC -> messageService.raw("sort-price-asc");
            case PRICE_DESC -> messageService.raw("sort-price-desc");
            case SELLER_ASC -> messageService.raw("sort-seller-asc");
        };
    }

    private String categoryName(AuctionCategory value) {
        return categoryNameByType.getOrDefault(value, value.name());
    }

    private Map<AuctionCategory, String> createCategoryNames(MessageService service) {
        Map<AuctionCategory, String> names = new EnumMap<>(AuctionCategory.class);
        names.put(AuctionCategory.ALL, service.raw("category-all"));
        names.put(AuctionCategory.BLOCKS, service.raw("category-blocks"));
        names.put(AuctionCategory.WEAPONS, service.raw("category-weapons"));
        names.put(AuctionCategory.TOOLS, service.raw("category-tools"));
        names.put(AuctionCategory.ARMOR, service.raw("category-armor"));
        names.put(AuctionCategory.FOOD, service.raw("category-food"));
        names.put(AuctionCategory.REDSTONE, service.raw("category-redstone"));
        names.put(AuctionCategory.OTHER, service.raw("category-other"));
        return names;
    }
}
