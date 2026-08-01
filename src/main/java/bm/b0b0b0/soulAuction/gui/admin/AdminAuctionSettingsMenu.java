package bm.b0b0b0.soulAuction.gui.admin;

import bm.b0b0b0.soulAuction.config.FakeActivityConfig;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivitySettings;
import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class AdminAuctionSettingsMenu implements InventoryHolder {

    private static final int TOGGLE_SLOT = 22;
    private static final int BACK_SLOT = 45;
    private static final int INFO_SLOT = 49;

    private static final Set<Integer> CONTENT_SLOTS = Set.of(TOGGLE_SLOT, BACK_SLOT, INFO_SLOT);

    private final UUID viewerId;
    private final String auctionId;
    private final int adminListPage;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final GuiGeneralSettings guiSettings;
    private final Supplier<FakeActivityConfig> fakeActivityConfigSupplier;
    private final Supplier<PluginConfig> configSupplier;
    private final Inventory inventory;

    public AdminAuctionSettingsMenu(
            UUID viewerId,
            String auctionId,
            int adminListPage,
            AuctionService auctionService,
            MessageService messageService,
            GuiGeneralSettings guiSettings,
            Supplier<FakeActivityConfig> fakeActivityConfigSupplier,
            Supplier<PluginConfig> configSupplier
    ) {
        this.viewerId = viewerId;
        this.auctionId = auctionId;
        this.adminListPage = adminListPage;
        this.auctionService = auctionService;
        this.messageService = messageService;
        this.guiSettings = guiSettings;
        this.fakeActivityConfigSupplier = fakeActivityConfigSupplier;
        this.configSupplier = configSupplier;
        this.inventory = Bukkit.createInventory(
                this,
                54,
                messageService.component(viewerId, "admin-settings-title", basePlaceholders())
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

    public String auctionId() {
        return auctionId;
    }

    public int adminListPage() {
        return adminListPage;
    }

    public boolean isBack(int slot) {
        return slot == BACK_SLOT;
    }

    public boolean isFakeToggle(int slot) {
        return slot == TOGGLE_SLOT;
    }

    public void refresh() {
        inventory.clear();
        AuctionDefinitionSettings definition = auctionService.findAuctionDefinition(auctionId);
        if (definition == null) {
            inventory.setItem(INFO_SLOT, item(
                    Material.BARRIER,
                    messageService.component(viewerId, "admin-settings-missing-title"),
                    messageService.components(viewerId, "admin-settings-missing-lore", basePlaceholders())
            ));
            inventory.setItem(BACK_SLOT, backItem());
            fillDecor();
            return;
        }
        Map<String, String> placeholders = fakePlaceholders(definition);
        String toggleLoreKey = definition.fakeActivityEnabled
                ? "admin-settings-fake-toggle-on-lore"
                : "admin-settings-fake-toggle-off-lore";
        Material toggleMaterial = definition.fakeActivityEnabled ? Material.LIME_DYE : Material.RED_DYE;
        inventory.setItem(TOGGLE_SLOT, item(
                toggleMaterial,
                messageService.component(viewerId, "admin-settings-fake-toggle-title", placeholders),
                messageService.components(viewerId, toggleLoreKey, placeholders)
        ));
        inventory.setItem(INFO_SLOT, item(
                Material.SHULKER_BOX,
                messageService.component(viewerId, "admin-settings-fake-info-title", placeholders),
                messageService.components(viewerId, "admin-settings-fake-info-lore", placeholders)
        ));
        inventory.setItem(BACK_SLOT, backItem());
        fillDecor();
    }

    private Map<String, String> basePlaceholders() {
        AuctionDefinitionSettings definition = auctionService.findAuctionDefinition(auctionId);
        if (definition == null) {
            return Map.of("id", auctionId, "auction", auctionId);
        }
        String display = definition.displayName == null || definition.displayName.isBlank()
                ? definition.id
                : definition.displayName;
        return Map.of("id", definition.id, "auction", display);
    }

    private Map<String, String> fakePlaceholders(AuctionDefinitionSettings definition) {
        FakeActivityConfig fakeConfig = fakeActivityConfigSupplier.get();
        FakeActivitySettings settings = fakeConfig == null ? null : fakeConfig.settings();
        int sellers = fakeConfig == null || fakeConfig.sellers().names == null
                ? 0
                : fakeConfig.sellers().names.size();
        int items = fakeConfig == null ? 0 : fakeConfig.items().size();
        String display = definition.displayName == null || definition.displayName.isBlank()
                ? definition.id
                : definition.displayName;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", definition.id);
        placeholders.put("auction", display);
        placeholders.put("fake", yesNo(definition.fakeActivityEnabled));
        placeholders.put("auction_file", "auctions/" + definition.id + ".yml");
        placeholders.put("synthetic", String.valueOf(auctionService.countSyntheticListings(definition.id)));
        placeholders.put("pool_sellers", String.valueOf(sellers));
        placeholders.put("pool_items", String.valueOf(items));
        placeholders.put("fake_max_per_auction", settings == null ? "0" : String.valueOf(settings.maxListingsPerAuction));
        placeholders.put("fake_max_total", settings == null ? "0" : String.valueOf(settings.maxTotalListings));
        placeholders.put("fake_tick_seconds", settings == null ? "0" : String.valueOf(settings.tickIntervalSeconds));
        placeholders.put("fake_file", configFakeDirectory());
        return placeholders;
    }

    private String configFakeDirectory() {
        PluginConfig config = configSupplier.get();
        if (config == null) {
            return "fake-activity";
        }
        return config.auctionSettings().fakeActivity.directory;
    }

    private ItemStack backItem() {
        return item(
                resolveMaterial(guiSettings.backButtonMaterial, Material.LIGHT_GRAY_DYE),
                messageService.component(viewerId, "admin-settings-back-title"),
                messageService.components(viewerId, "admin-settings-back-lore")
        );
    }

    private ItemStack item(Material material, Component title, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(title);
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void fillDecor() {
        ItemStack decor = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (CONTENT_SLOTS.contains(slot)) {
                continue;
            }
            inventory.setItem(slot, decor);
        }
    }

    private static Material resolveMaterial(String materialName, Material fallback) {
        if (materialName == null || materialName.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(materialName);
        return material == null ? fallback : material;
    }

    private String yesNo(boolean value) {
        return value ? messageService.raw(viewerId, "admin-label-yes") : messageService.raw(viewerId, "admin-label-no");
    }
}
