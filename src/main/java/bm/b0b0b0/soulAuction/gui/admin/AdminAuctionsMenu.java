package bm.b0b0b0.soulAuction.gui.admin;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminAuctionsMenu implements InventoryHolder {

    private static final int PAGE_SIZE = 45;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int EMPTY_SLOT = 22;

    private final UUID viewerId;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final GuiGeneralSettings guiSettings;
    private final Inventory inventory;
    private final Map<Integer, String> auctionIdBySlot = new HashMap<>();
    private int page;

    public AdminAuctionsMenu(
            UUID viewerId,
            int page,
            AuctionService auctionService,
            MessageService messageService,
            GuiGeneralSettings guiSettings
    ) {
        this.viewerId = viewerId;
        this.page = Math.max(0, page);
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.guiSettings = guiSettings;
        this.inventory = Bukkit.createInventory(
                this,
                54,
                messageService.component(viewerId, "admin-auctions-title", pagePlaceholders())
        );
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

    public String auctionIdAt(int slot) {
        return auctionIdBySlot.get(slot);
    }

    public boolean isPrev(int slot) {
        return slot == PREV_SLOT;
    }

    public boolean isNext(int slot) {
        return slot == NEXT_SLOT;
    }

    public boolean isInfo(int slot) {
        return slot == INFO_SLOT;
    }

    public int maxPageIndex() {
        return maxPage(sortedDefinitions().size());
    }

    public void refresh() {
        inventory.clear();
        auctionIdBySlot.clear();
        List<AuctionDefinitionSettings> all = sortedDefinitions();
        int maxPage = maxPage(all.size());
        if (page > maxPage) {
            page = maxPage;
        }
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, all.size());
        if (from >= to) {
            inventory.setItem(
                    EMPTY_SLOT,
                    paper(
                            messageService.component(viewerId, "admin-auctions-empty-title"),
                            messageService.components(viewerId, "admin-auctions-empty-lore")
                    )
            );
        } else {
            for (int i = from; i < to; i++) {
                AuctionDefinitionSettings definition = all.get(i);
                int slot = i - from;
                auctionIdBySlot.put(slot, definition.id);
                inventory.setItem(slot, auctionItem(definition));
            }
        }
        if (page > 0) {
            inventory.setItem(PREV_SLOT, navItem(guiSettings.previousPageMaterial, "button-prev-page", "admin-auctions-prev-lore"));
        }
        if (page < maxPage) {
            inventory.setItem(NEXT_SLOT, navItem(guiSettings.nextPageMaterial, "button-next-page", "admin-auctions-next-lore"));
        }
        inventory.setItem(INFO_SLOT, paper(
                messageService.component(viewerId, "admin-auctions-info-title", pagePlaceholders()),
                messageService.components(viewerId, "admin-auctions-info-lore", summaryPlaceholders(all.size()))
        ));
        fillDecor();
    }

    private List<AuctionDefinitionSettings> sortedDefinitions() {
        return auctionService.sortedAuctionDefinitions();
    }

    private static int maxPage(int total) {
        if (total <= 0) {
            return 0;
        }
        return Math.max(0, (total - 1) / PAGE_SIZE);
    }

    private Map<String, String> pagePlaceholders() {
        List<AuctionDefinitionSettings> all = sortedDefinitions();
        int maxPage = maxPage(all.size());
        return Map.of(
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(maxPage + 1),
                "total", String.valueOf(all.size())
        );
    }

    private Map<String, String> summaryPlaceholders(int totalAuctions) {
        int listed = auctionService.totalListingsCount();
        return Map.of(
                "page", String.valueOf(page + 1),
                "pages", String.valueOf(maxPage(totalAuctions) + 1),
                "total", String.valueOf(totalAuctions),
                "listings", String.valueOf(listed)
        );
    }

    private ItemStack auctionItem(AuctionDefinitionSettings definition) {
        Material material = Material.CHEST;
        if (definition.id.equalsIgnoreCase(auctionService.defaultAuctionId())) {
            material = Material.ENDER_CHEST;
        }
        String display = definition.displayName == null || definition.displayName.isBlank()
                ? definition.id
                : definition.displayName;
        int listings = auctionService.countActiveListings(definition.id);
        boolean isDefault = definition.id.equalsIgnoreCase(auctionService.defaultAuctionId());
        Map<String, String> placeholders = Map.of(
                "id", definition.id,
                "auction", display,
                "economy", definition.economy == null ? "VAULT" : definition.economy.toUpperCase(Locale.ROOT),
                "listings", String.valueOf(listings),
                "buy", definition.buyEnabled ? yesLabel() : noLabel(),
                "sell", definition.sellEnabled ? yesLabel() : noLabel(),
                "default", isDefault ? yesLabel() : noLabel()
        );
        return actionItem(
                material,
                messageService.component(viewerId, "admin-auctions-entry-title", placeholders),
                messageService.components(viewerId, "admin-auctions-entry-lore", placeholders)
        );
    }

    private String yesLabel() {
        return messageService.raw(viewerId, "admin-label-yes");
    }

    private String noLabel() {
        return messageService.raw(viewerId, "admin-label-no");
    }

    private ItemStack navItem(String materialName, String titleKey, String loreKey) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.PAPER;
        }
        return actionItem(
                material,
                messageService.component(viewerId, titleKey),
                messageService.components(viewerId, loreKey, pagePlaceholders())
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
        for (int slot = 45; slot < 54; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, decor);
            }
        }
    }
}
