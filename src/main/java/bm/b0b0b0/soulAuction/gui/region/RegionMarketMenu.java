package bm.b0b0b0.soulAuction.gui.region;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.gui.GuiFillerItem;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.service.region.RegionBrowseService.BrowsePage;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
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

public final class RegionMarketMenu implements InventoryHolder {

    private final UUID viewerId;
    private final RegionMarketService regionMarketService;
    private final MessageService messageService;
    private final GuiGeneralSettings guiSettings;
    private final AuctionSettings.RegionMarketSettings regionSettings;
    private final Inventory inventory;
    private final Map<Integer, Long> listingBySlot = new HashMap<>();
    private int page;
    private AuctionSort sort;
    private UUID sellerFilter;

    public RegionMarketMenu(
            UUID viewerId,
            RegionMarketService regionMarketService,
            MessageService messageService,
            GuiGeneralSettings guiSettings,
            AuctionSettings.RegionMarketSettings regionSettings
    ) {
        this(viewerId, regionMarketService, messageService, guiSettings, regionSettings, 0, AuctionSort.NEWEST, null);
    }

    public RegionMarketMenu(
            UUID viewerId,
            RegionMarketService regionMarketService,
            MessageService messageService,
            GuiGeneralSettings guiSettings,
            AuctionSettings.RegionMarketSettings regionSettings,
            int initialPage,
            AuctionSort sort,
            UUID sellerFilter
    ) {
        this.viewerId = viewerId;
        this.regionMarketService = regionMarketService;
        this.messageService = messageService;
        this.guiSettings = guiSettings;
        this.regionSettings = regionSettings;
        this.page = Math.max(0, initialPage);
        this.sort = sort == null ? AuctionSort.NEWEST : sort;
        this.sellerFilter = sellerFilter;
        this.inventory = Bukkit.createInventory(this, guiSettings.size, messageService.component(viewerId, "region-market-title"));
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public int page() {
        return page;
    }

    public AuctionSort sort() {
        return sort;
    }

    public Long listingIdAt(int slot) {
        return listingBySlot.get(slot);
    }

    public void click(int slot) {
        if (slot == guiSettings.previousPageSlot) {
            if (page > 0) {
                page--;
                refresh();
            }
            return;
        }
        if (slot == guiSettings.nextPageSlot) {
            page++;
            refresh();
            return;
        }
        if (slot == guiSettings.sortSlot) {
            sort = sort.next();
            refresh();
            return;
        }
        if (slot == guiSettings.refreshSlot) {
            refresh();
            return;
        }
        if (slot == regionSettings.sellButtonSlot) {
            return;
        }
    }

    public boolean isSellButton(int slot) {
        return slot == regionSettings.sellButtonSlot;
    }

    public void refresh() {
        listingBySlot.clear();
        inventory.clear();
        ItemStack filler = GuiFillerItem.create(guiSettings, null);
        for (int borderSlot : guiSettings.borderSlots) {
            inventory.setItem(borderSlot, filler);
        }
        List<Integer> listingSlots = guiSettings.listingSlots;
        int visible = listingSlots.size();
        BrowsePage browsePage = regionMarketService.browsePage(sort, page, visible, sellerFilter);
        int total = browsePage.total();
        int maxPage = Math.max(0, (total - 1) / visible);
        if (page > maxPage) {
            page = maxPage;
            browsePage = regionMarketService.browsePage(sort, page, visible, sellerFilter);
            total = browsePage.total();
        }
        List<AuctionListing> listings = browsePage.listings();
        for (int index = 0; index < listings.size() && index < listingSlots.size(); index++) {
            AuctionListing listing = listings.get(index);
            int slot = listingSlots.get(index);
            AuctionDefinitionSettings definition = regionMarketService.findDefinition(listing.auctionId());
            ItemStack icon = regionMarketService.displayItemFactory().createListingIcon(
                    viewerId,
                    listing,
                    definition,
                    regionSettings
            );
            inventory.setItem(slot, icon);
            listingBySlot.put(slot, listing.listingId());
        }
        inventory.setItem(guiSettings.previousPageSlot, controlButton(
                guiSettings.previousPageMaterial,
                guiSettings.previousPageCustomModelData,
                page > 0 ? "button-previous-page" : "button-previous-page-disabled",
                Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(maxPage + 1))
        ));
        inventory.setItem(guiSettings.nextPageSlot, controlButton(
                guiSettings.nextPageMaterial,
                guiSettings.nextPageCustomModelData,
                page < maxPage ? "button-next-page" : "button-next-page-disabled",
                Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(maxPage + 1))
        ));
        inventory.setItem(guiSettings.sortSlot, controlButton(
                guiSettings.sortMaterial,
                guiSettings.sortCustomModelData,
                sort.messageKey(),
                Map.of()
        ));
        inventory.setItem(guiSettings.refreshSlot, controlButton(
                guiSettings.refreshMaterial,
                guiSettings.refreshCustomModelData,
                "region-button-refresh",
                Map.of("total", String.valueOf(total))
        ));
        inventory.setItem(regionSettings.sellButtonSlot, controlButton(
                "EMERALD",
                -1,
                "region-button-sell",
                Map.of()
        ));
    }

    private ItemStack controlButton(String materialName, int customModelData, String titleKey, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        Component title = messageService.component(viewerId, titleKey, placeholders);
        meta.displayName(title);
        String loreKey = titleKey + "-lore";
        List<Component> lore = messageService.components(viewerId, loreKey, placeholders);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        if (customModelData >= 0) {
            meta.setCustomModelData(customModelData);
        }
        item.setItemMeta(meta);
        return item;
    }
}
