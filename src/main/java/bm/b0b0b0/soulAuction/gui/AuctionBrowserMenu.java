package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowseFilterState;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowsePage;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.ListingItemPresentation;
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
    private int page;
    private AuctionSort sort;
    private AuctionCategory category;
    private String searchQuery;
    private boolean favoritesOnly;

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
                messageService.component(viewerId, "auction-title", auctionService.auctionGuiTitlePlaceholders(auctionId))
        );
        this.listingBySlot = new HashMap<>();
        this.page = Math.max(0, initialPage);
        this.sort = AuctionSort.NEWEST;
        this.category = AuctionCategory.ALL;
        BrowseFilterState filterState = auctionService.browseFilterState(viewerId);
        this.favoritesOnly = filterState.favoritesOnly();
        if (searchQuery == null || searchQuery.isBlank()) {
            this.searchQuery = filterState.searchQuery();
        } else {
            this.searchQuery = searchQuery.trim();
        }
        refresh();
    }

    public String searchQuery() {
        return searchQuery;
    }

    public String auctionId() {
        return auctionId;
    }

    public UUID viewerId() {
        return viewerId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
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
        if (slot == guiSettings.searchSlot) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(viewerId);
            if (player != null) {
                auctionService.beginPendingChatSearch(viewerId, auctionId);
                player.closeInventory();
                messageService.send(player, "search-chat-prompt");
            }
            return;
        }
        if (slot == guiSettings.favoritesSlot) {
            favoritesOnly = !favoritesOnly;
            BrowseFilterState current = auctionService.browseFilterState(viewerId);
            auctionService.setBrowseFilterState(
                    viewerId,
                    new BrowseFilterState(
                            current.searchQuery(),
                            favoritesOnly,
                            current.favoriteListingsOnly(),
                            current.minPrice(),
                            current.maxPrice(),
                            current.sellerFilter()
                    )
            );
            page = 0;
            refresh();
            return;
        }
        if (slot == guiSettings.priceFilterSlot) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(viewerId);
            if (player != null) {
                AuctionPriceFilterMenu filterMenu = new AuctionPriceFilterMenu(
                        viewerId,
                        auctionId,
                        auctionService,
                        messageService,
                        guiSettings
                );
                player.openInventory(filterMenu.getInventory());
            }
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
        BrowseFilterState state = auctionService.browseFilterState(viewerId);
        BrowseFilterState filter = new BrowseFilterState(
                searchQuery, favoritesOnly, state.favoriteListingsOnly(), state.minPrice(), state.maxPrice(), state.sellerFilter()
        );
        BrowsePage browsePage = auctionService.browsePage(
                auctionId, sort, category, page, visible, searchQuery, viewerId, filter
        );
        int total = browsePage.total();
        int maxPage = Math.max(0, (total - 1) / visible);
        List<AuctionListing> listings = browsePage.listings();
        for (int i = 0; i < listings.size() && i < listingSlots.size(); i++) {
            AuctionListing listing = listings.get(i);
            int slot = listingSlots.get(i);
            ItemStack item = ItemStackCodec.decode(listing.itemBase64());
            ListingItemPresentation.applyAuctionGuiName(item);
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta != null) {
                java.util.ArrayList<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>(messageService.components(
                        viewerId,
                        "listing-lore",
                        auctionService.listingLorePlaceholders(listing, viewerId)
                ));
                if (auctionService.listingExpiryEnabled(auctionId)) {
                    lore.addAll(messageService.components(
                            viewerId,
                            "listing-lore-expires",
                            auctionService.listingLorePlaceholders(listing, viewerId)
                    ));
                }
                lore.addAll(messageService.componentsFromTemplates(
                        viewerId,
                        auctionService.listingLoreTemplate(auctionId),
                        auctionService.listingLorePlaceholders(listing, viewerId)
                ));
                if (!listing.sellerId().equals(viewerId)) {
                    if (auctionService.isFavoriteSeller(viewerId, listing.sellerId())) {
                        lore.addAll(messageService.components(viewerId, "listing-lore-favorite-seller-active"));
                    } else {
                        lore.addAll(messageService.components(viewerId, "listing-lore-favorite-seller-inactive"));
                    }
                }
                if (auctionService.isFavoriteListing(viewerId, listing.listingId())) {
                    lore.addAll(messageService.components(viewerId, "listing-lore-favorite-listing"));
                } else {
                    lore.addAll(messageService.components(viewerId, "listing-lore-favorite-listing-inactive"));
                }
                itemMeta.lore(lore);
                item.setItemMeta(itemMeta);
            }
            inventory.setItem(slot, item);
            listingBySlot.put(slot, listing.listingId());
        }
        if (page > 0) {
            inventory.setItem(
                    guiSettings.previousPageSlot,
                    actionItem(
                            guiSettings.previousPageMaterial,
                            guiSettings.previousPageCustomModelData,
                            messageService.component(viewerId, "button-prev-page"),
                            messageService.components(viewerId, "button-prev-page-lore")
                    )
            );
        }
        if (page < maxPage) {
            inventory.setItem(
                    guiSettings.nextPageSlot,
                    actionItem(
                            guiSettings.nextPageMaterial,
                            guiSettings.nextPageCustomModelData,
                            messageService.component(viewerId, "button-next-page"),
                            messageService.components(viewerId, "button-next-page-lore")
                    )
            );
        }
        inventory.setItem(
                guiSettings.historySlot,
                actionItem(
                        guiSettings.historyMaterial,
                        guiSettings.historyCustomModelData,
                        messageService.component(viewerId, "button-history"),
                        messageService.components(viewerId, "button-history-lore")
                )
        );
        inventory.setItem(
                guiSettings.refreshSlot,
                actionItem(
                        guiSettings.refreshMaterial,
                        guiSettings.refreshCustomModelData,
                        searchQuery == null
                                ? messageService.component(viewerId, "button-refresh")
                                : messageService.component(viewerId, "button-refresh-search", Map.of("query", searchQuery)),
                        searchQuery == null
                                ? messageService.components(viewerId, "button-refresh-lore")
                                : messageService.components(viewerId, "button-refresh-search-lore", Map.of("query", searchQuery))
                )
        );
        inventory.setItem(
                guiSettings.sortSlot,
                actionItem(
                        guiSettings.sortMaterial,
                        guiSettings.sortCustomModelData,
                        messageService.component(viewerId, "button-sort"),
                        messageService.components(viewerId, "button-sort-lore", Map.of("sort", sortName(sort)))
                )
        );
        inventory.setItem(
                guiSettings.categorySlot,
                actionItem(
                        guiSettings.categoryMaterial,
                        guiSettings.categoryCustomModelData,
                        messageService.component(viewerId, "button-category"),
                        messageService.components(viewerId, "button-category-lore", Map.of("category", categoryName(category)))
                )
        );
        inventory.setItem(
                guiSettings.searchSlot,
                actionItem(
                        guiSettings.searchMaterial,
                        guiSettings.searchCustomModelData,
                        messageService.component(viewerId, "button-search"),
                        messageService.components(viewerId, "button-search-lore")
                )
        );
        inventory.setItem(
                guiSettings.favoritesSlot,
                actionItem(
                        guiSettings.favoritesMaterial,
                        guiSettings.favoritesCustomModelData,
                        favoritesOnly
                                ? messageService.component(viewerId, "button-favorites-on")
                                : messageService.component(viewerId, "button-favorites-off"),
                        favoritesOnly
                                ? messageService.components(viewerId, "button-favorites-on-lore")
                                : messageService.components(viewerId, "button-favorites-off-lore")
                )
        );
        inventory.setItem(
                guiSettings.priceFilterSlot,
                actionItem(
                        guiSettings.priceFilterMaterial,
                        guiSettings.priceFilterCustomModelData,
                        messageService.component(viewerId, "button-price-filter"),
                        messageService.components(viewerId, "button-price-filter-lore")
                )
        );
    }

    public void nextPage() {
        int visible = guiSettings.listingSlots.size();
        BrowseFilterState state = auctionService.browseFilterState(viewerId);
        BrowseFilterState filter = new BrowseFilterState(
                searchQuery, favoritesOnly, state.favoriteListingsOnly(), state.minPrice(), state.maxPrice(), state.sellerFilter()
        );
        int total = auctionService.count(auctionId, category, searchQuery, viewerId, filter);
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
        return actionItem(materialName, customModelData, title, null);
    }

    private ItemStack actionItem(
            String materialName,
            int customModelData,
            Component title,
            java.util.List<Component> lore
    ) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            itemMeta.displayName(title);
            if (lore != null && !lore.isEmpty()) {
                itemMeta.lore(lore);
            }
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
            case NEWEST -> messageService.raw(viewerId, "sort-newest");
            case OLDEST -> messageService.raw(viewerId, "sort-oldest");
            case PRICE_ASC -> messageService.raw(viewerId, "sort-price-asc");
            case PRICE_DESC -> messageService.raw(viewerId, "sort-price-desc");
            case SELLER_ASC -> messageService.raw(viewerId, "sort-seller-asc");
            case SELLER_DESC -> messageService.raw(viewerId, "sort-seller-desc");
            case AMOUNT_ASC -> messageService.raw(viewerId, "sort-amount-asc");
            case AMOUNT_DESC -> messageService.raw(viewerId, "sort-amount-desc");
            case MATERIAL_ASC -> messageService.raw(viewerId, "sort-material-asc");
            case MATERIAL_DESC -> messageService.raw(viewerId, "sort-material-desc");
            case CATEGORY_ASC -> messageService.raw(viewerId, "sort-category-asc");
            case LISTING_ID_ASC -> messageService.raw(viewerId, "sort-id-asc");
            case LISTING_ID_DESC -> messageService.raw(viewerId, "sort-id-desc");
            case UNIT_PRICE_ASC -> messageService.raw(viewerId, "sort-unit-price-asc");
            case UNIT_PRICE_DESC -> messageService.raw(viewerId, "sort-unit-price-desc");
        };
    }

    private String categoryName(AuctionCategory value) {
        return switch (value) {
            case ALL -> messageService.raw(viewerId, "category-all");
            case BLOCKS -> messageService.raw(viewerId, "category-blocks");
            case WEAPONS -> messageService.raw(viewerId, "category-weapons");
            case TOOLS -> messageService.raw(viewerId, "category-tools");
            case ARMOR -> messageService.raw(viewerId, "category-armor");
            case FOOD -> messageService.raw(viewerId, "category-food");
            case REDSTONE -> messageService.raw(viewerId, "category-redstone");
            case OTHER -> messageService.raw(viewerId, "category-other");
        };
    }
}
