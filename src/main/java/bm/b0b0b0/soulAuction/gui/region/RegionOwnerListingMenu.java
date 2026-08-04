package bm.b0b0b0.soulAuction.gui.region;

import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.model.ListingMetadata;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
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

public final class RegionOwnerListingMenu implements InventoryHolder {

    private static final int ICON_SLOT = 22;
    private static final int PRICE_SLOT = 31;
    private static final int MINUS_SMALL_SLOT = 28;
    private static final int MINUS_BIG_SLOT = 29;
    private static final int PLUS_SMALL_SLOT = 33;
    private static final int PLUS_BIG_SLOT = 34;
    private static final int DESCRIPTION_SLOT = 24;
    private static final int REMOVE_SLOT = 20;
    private static final int BACK_SLOT = 45;
    private static final int APPLY_SLOT = 53;

    private final UUID viewerId;
    private final long listingId;
    private final UUID returnSellerFilter;
    private final int returnPage;
    private final AuctionSort returnSort;
    private final RegionMarketService regionMarketService;
    private final MessageService messageService;
    private final Inventory inventory;
    private int editedPrice;

    public RegionOwnerListingMenu(
            UUID viewerId,
            long listingId,
            UUID returnSellerFilter,
            int returnPage,
            AuctionSort returnSort,
            RegionMarketService regionMarketService,
            MessageService messageService
    ) {
        this.viewerId = viewerId;
        this.listingId = listingId;
        this.returnSellerFilter = returnSellerFilter;
        this.returnPage = returnPage;
        this.returnSort = returnSort;
        this.regionMarketService = regionMarketService;
        this.messageService = messageService;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component(viewerId, "region-owner-menu-title"));
        AuctionListing listing = regionMarketService.listingById(listingId);
        this.editedPrice = listing == null ? 1 : listing.price();
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public long listingId() {
        return listingId;
    }

    public UUID returnSellerFilter() {
        return returnSellerFilter;
    }

    public int returnPage() {
        return returnPage;
    }

    public AuctionSort returnSort() {
        return returnSort;
    }

    public int editedPrice() {
        return editedPrice;
    }

    public void click(int slot) {
        if (slot == MINUS_SMALL_SLOT) {
            changePrice(-100);
            return;
        }
        if (slot == MINUS_BIG_SLOT) {
            changePrice(-1000);
            return;
        }
        if (slot == PLUS_SMALL_SLOT) {
            changePrice(100);
            return;
        }
        if (slot == PLUS_BIG_SLOT) {
            changePrice(1000);
        }
    }

    public boolean isApply(int slot) {
        return slot == APPLY_SLOT;
    }

    public boolean isRemove(int slot) {
        return slot == REMOVE_SLOT;
    }

    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    public boolean isDescription(int slot) {
        return slot == DESCRIPTION_SLOT;
    }

    public void refresh() {
        inventory.clear();
        AuctionListing listing = regionMarketService.listingById(listingId);
        String auctionId = listing == null ? "global" : listing.auctionId();
        String livePrice = listing == null
                ? "?"
                : regionMarketService.formatPrice(listing.price(), listing.auctionId(), viewerId);
        String formattedEdited = regionMarketService.formatPrice(editedPrice, auctionId, viewerId);
        Map<String, String> pricePlaceholders = Map.of("price", formattedEdited);
        inventory.setItem(MINUS_SMALL_SLOT, actionItem(
                Material.RED_DYE,
                messageService.component(viewerId, "sell-price-minus-small", Map.of("step", regionMarketService.formatPrice(100, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(MINUS_BIG_SLOT, actionItem(
                Material.REDSTONE,
                messageService.component(viewerId, "sell-price-minus-big", Map.of("step", regionMarketService.formatPrice(1000, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(PLUS_SMALL_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component(viewerId, "sell-price-plus-small", Map.of("step", regionMarketService.formatPrice(100, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(PLUS_BIG_SLOT, actionItem(
                Material.EMERALD,
                messageService.component(viewerId, "sell-price-plus-big", Map.of("step", regionMarketService.formatPrice(1000, auctionId, viewerId))),
                messageService.components(viewerId, "sell-price-button-lore", pricePlaceholders)
        ));
        inventory.setItem(PRICE_SLOT, actionItem(
                Material.NAME_TAG,
                messageService.component(viewerId, "sell-price-current", pricePlaceholders)
        ));
        inventory.setItem(APPLY_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component(viewerId, "owner-price-apply"),
                messageService.components(viewerId, "owner-price-lore", Map.of(
                        "edited", formattedEdited,
                        "current", livePrice
                ))
        ));
        ListingMetadata metadata = listing == null ? ListingMetadata.empty() : listing.metadata();
        String descriptionText = metadata.regionDescription == null || metadata.regionDescription.isBlank()
                ? "—"
                : metadata.regionDescription.trim();
        inventory.setItem(DESCRIPTION_SLOT, actionItem(
                Material.WRITABLE_BOOK,
                messageService.component(viewerId, "region-owner-description-button"),
                messageService.components(viewerId, "region-owner-description-lore", Map.of("description", descriptionText))
        ));
        inventory.setItem(REMOVE_SLOT, actionItem(Material.RED_WOOL, messageService.component(viewerId, "owner-remove-listing")));
        inventory.setItem(BACK_SLOT, actionItem(Material.LIGHT_GRAY_DYE, messageService.component(viewerId, "owner-back")));
        if (listing != null) {
            inventory.setItem(
                    ICON_SLOT,
                    regionMarketService.displayItemFactory().createListingIcon(
                            viewerId,
                            listing,
                            regionMarketService.findDefinition(listing.auctionId()),
                            regionMarketService.settings()
                    )
            );
        }
        fillDecor();
    }

    private void changePrice(int delta) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(viewerId);
        AuctionListing listing = regionMarketService.listingById(listingId);
        String auctionId = listing == null ? "global" : listing.auctionId();
        int max = player == null
                ? regionMarketService.globalMaxPrice()
                : regionMarketService.maxPrice(player, auctionId);
        int next = editedPrice + delta;
        if (next < 1) {
            next = 1;
        }
        if (next > max) {
            next = max;
        }
        editedPrice = next;
        refresh();
    }

    private void fillDecor() {
        ItemStack decor = actionItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot == ICON_SLOT || slot == PRICE_SLOT || slot == MINUS_SMALL_SLOT || slot == MINUS_BIG_SLOT
                    || slot == PLUS_SMALL_SLOT || slot == PLUS_BIG_SLOT || slot == REMOVE_SLOT || slot == BACK_SLOT
                    || slot == APPLY_SLOT || slot == DESCRIPTION_SLOT) {
                continue;
            }
            inventory.setItem(slot, decor);
        }
    }

    private ItemStack actionItem(Material material, Component title) {
        return actionItem(material, title, null);
    }

    private ItemStack actionItem(Material material, Component title, List<Component> lore) {
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
}
