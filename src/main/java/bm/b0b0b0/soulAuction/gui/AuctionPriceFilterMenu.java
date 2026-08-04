package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowseFilterState;
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

public final class AuctionPriceFilterMenu implements InventoryHolder {

    private static final int HELP_SLOT = 4;
    private static final int CLEAR_SLOT = 20;
    private static final int RANGE_SLOT = 22;
    private static final int MIN_MINUS_SLOT = 28;
    private static final int MIN_LABEL_SLOT = 30;
    private static final int MIN_PLUS_SLOT = 32;
    private static final int MAX_MINUS_SLOT = 37;
    private static final int MAX_LABEL_SLOT = 39;
    private static final int MAX_PLUS_SLOT = 41;
    private static final int BACK_SLOT = 45;
    private static final int APPLY_SLOT = 53;

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
        this.inventory = Bukkit.createInventory(this, 54, messageService.component(viewerId, "price-filter-title"));
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
        if (slot == MIN_MINUS_SLOT) {
            draftMin = Math.max(0, draftMin - 100);
        } else if (slot == MIN_PLUS_SLOT) {
            draftMin = draftMin + 100;
        } else if (slot == MAX_MINUS_SLOT) {
            draftMax = Math.max(0, draftMax - 100);
        } else if (slot == MAX_PLUS_SLOT) {
            draftMax = draftMax + 100;
        } else if (slot == CLEAR_SLOT) {
            draftMin = 0;
            draftMax = 0;
        } else if (slot == APPLY_SLOT) {
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
        } else if (slot == BACK_SLOT) {
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
        if (!auctionService.guardAuctionAccess(player, auctionId)) {
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
        String maxFormatted = draftMax <= 0
                ? messageService.raw(viewerId, "price-filter-max-unlimited")
                : auctionService.formatPrice(draftMax, auctionId, viewerId);
        Map<String, String> rangePlaceholders = Map.of("min", minFormatted, "max", maxFormatted);

        inventory.setItem(HELP_SLOT, actionItem(
                Material.BOOK,
                messageService.component(viewerId, "price-filter-help"),
                messageService.components(viewerId, "price-filter-help-lore")
        ));
        inventory.setItem(RANGE_SLOT, actionItem(
                Material.GOLD_NUGGET,
                messageService.component(viewerId, "price-filter-range"),
                messageService.components(viewerId, "price-filter-range-lore", rangePlaceholders)
        ));
        inventory.setItem(MIN_LABEL_SLOT, actionItem(
                Material.NAME_TAG,
                messageService.component(viewerId, "price-filter-min-label", Map.of("min", minFormatted)),
                null
        ));
        inventory.setItem(MAX_LABEL_SLOT, actionItem(
                Material.NAME_TAG,
                messageService.component(viewerId, "price-filter-max-label", Map.of("max", maxFormatted)),
                null
        ));
        inventory.setItem(MIN_MINUS_SLOT, actionItem(
                Material.RED_DYE,
                messageService.component(viewerId, "price-filter-min-minus", Map.of("step", step)),
                messageService.components(viewerId, "price-filter-min-minus-lore", Map.of("step", step))
        ));
        inventory.setItem(MIN_PLUS_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component(viewerId, "price-filter-min-plus", Map.of("step", step)),
                messageService.components(viewerId, "price-filter-min-plus-lore", Map.of("step", step))
        ));
        inventory.setItem(MAX_MINUS_SLOT, actionItem(
                Material.RED_DYE,
                messageService.component(viewerId, "price-filter-max-minus", Map.of("step", step)),
                messageService.components(viewerId, "price-filter-max-minus-lore", Map.of("step", step))
        ));
        inventory.setItem(MAX_PLUS_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component(viewerId, "price-filter-max-plus", Map.of("step", step)),
                messageService.components(viewerId, "price-filter-max-plus-lore", Map.of("step", step))
        ));
        inventory.setItem(CLEAR_SLOT, actionItem(
                Material.RED_DYE,
                messageService.component(viewerId, "price-filter-clear"),
                messageService.components(viewerId, "price-filter-clear-lore")
        ));
        Material backMaterial = Material.matchMaterial(guiSettings.backButtonMaterial);
        if (backMaterial == null) {
            backMaterial = Material.LIGHT_GRAY_DYE;
        }
        inventory.setItem(BACK_SLOT, actionItem(
                backMaterial,
                messageService.component(viewerId, "price-filter-back"),
                messageService.components(viewerId, "price-filter-back-lore")
        ));
        inventory.setItem(APPLY_SLOT, actionItem(
                Material.LIME_DYE,
                messageService.component(viewerId, "price-filter-apply"),
                messageService.components(viewerId, "price-filter-apply-lore", rangePlaceholders)
        ));
        fillDecor();
    }

    private void fillDecor() {
        ItemStack decor = actionItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isInteractiveOrDisplay(slot)) {
                continue;
            }
            inventory.setItem(slot, decor);
        }
    }

    private static boolean isInteractiveOrDisplay(int slot) {
        return slot == HELP_SLOT
                || slot == CLEAR_SLOT
                || slot == RANGE_SLOT
                || slot == MIN_MINUS_SLOT
                || slot == MAX_MINUS_SLOT
                || slot == MIN_LABEL_SLOT
                || slot == MAX_LABEL_SLOT
                || slot == MIN_PLUS_SLOT
                || slot == MAX_PLUS_SLOT
                || slot == BACK_SLOT
                || slot == APPLY_SLOT;
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
}
