package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.ListingItemPresentation;
import java.util.ArrayList;
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

public final class FavoriteListingsMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int EMPTY_SLOT = 22;

    private final UUID viewerId;
    private final String auctionId;
    private final GuiReturnTarget returnTarget;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final GuiGeneralSettings guiSettings;
    private final Inventory inventory;
    private final Map<Integer, Long> listingBySlot = new HashMap<>();
    private int page;

    public FavoriteListingsMenu(
            UUID viewerId,
            String auctionId,
            int page,
            GuiReturnTarget returnTarget,
            AuctionService auctionService,
            MessageService messageService,
            GuiGeneralSettings guiSettings
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.page = Math.max(0, page);
        this.returnTarget = returnTarget == null ? GuiReturnTarget.BROWSER : returnTarget;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.guiSettings = guiSettings;
        this.inventory = Bukkit.createInventory(this, 54, messageService.component(viewerId, "favorite-listings-title"));
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

    public GuiReturnTarget returnTarget() {
        return returnTarget;
    }

    public int page() {
        return page;
    }

    public Long listingIdAt(int slot) {
        return listingBySlot.get(slot);
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
        List<AuctionListing> all = auctionService.favoriteListingsForViewer(viewerId);
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (page < maxPage) {
            page++;
            refresh();
        }
    }

    public void refresh() {
        inventory.clear();
        listingBySlot.clear();
        List<AuctionListing> all = auctionService.favoriteListingsForViewer(viewerId);
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        if (page > maxPage) {
            page = maxPage;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());
        if (from >= to) {
            inventory.setItem(EMPTY_SLOT, paper(
                    messageService.component(viewerId, "favorite-listings-empty-title"),
                    messageService.components(viewerId, "favorite-listings-empty-lore")
            ));
        } else {
            for (int i = from; i < to; i++) {
                AuctionListing listing = all.get(i);
                int slot = i - from;
                listingBySlot.put(slot, listing.listingId());
                inventory.setItem(slot, listingItem(listing));
            }
        }
        if (page > 0) {
            inventory.setItem(PREV_SLOT, navItem(
                    guiSettings.previousPageMaterial,
                    "button-prev-page",
                    "favorite-listings-prev-lore"
            ));
        }
        if (page < maxPage) {
            inventory.setItem(NEXT_SLOT, navItem(
                    guiSettings.nextPageMaterial,
                    "button-next-page",
                    "favorite-listings-next-lore"
            ));
        }
        Material backMaterial = Material.matchMaterial(guiSettings.backButtonMaterial);
        if (backMaterial == null) {
            backMaterial = Material.LIGHT_GRAY_DYE;
        }
        String backTitleKey = returnTarget == GuiReturnTarget.HUB ? "favorite-listings-back-hub" : "favorite-listings-back";
        String backLoreKey = returnTarget == GuiReturnTarget.HUB ? "favorite-listings-back-hub-lore" : "favorite-listings-back-lore";
        inventory.setItem(BACK_SLOT, actionItem(
                backMaterial,
                messageService.component(viewerId, backTitleKey),
                messageService.components(viewerId, backLoreKey)
        ));
        fillDecor();
    }

    private ItemStack listingItem(AuctionListing listing) {
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        ListingItemPresentation.applyAuctionGuiName(item);
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta != null) {
            ArrayList<Component> lore = new ArrayList<>(messageService.components(
                    viewerId,
                    "listing-lore",
                    auctionService.listingLorePlaceholders(listing, viewerId)
            ));
            lore.addAll(messageService.components(viewerId, "listing-lore-favorite-listing"));
            itemMeta.lore(lore);
            item.setItemMeta(itemMeta);
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
}
