package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.ListingMetadata;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RegionDisplayItemFactory {

    private final MessageService messageService;
    private final AuctionEconomyService economy;
    private final WorldGuardBridge worldGuardBridge;

    public RegionDisplayItemFactory(
            MessageService messageService,
            AuctionEconomyService economy,
            WorldGuardBridge worldGuardBridge
    ) {
        this.messageService = messageService;
        this.economy = economy;
        this.worldGuardBridge = worldGuardBridge;
    }

    public ItemStack createListingIcon(
            UUID viewerId,
            AuctionListing listing,
            AuctionDefinitionSettings definition,
            AuctionSettings.RegionMarketSettings settings
    ) {
        Material material = parseMaterial(settings == null ? "MAP" : settings.listIconMaterial, Material.MAP);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        ListingMetadata metadata = listing.metadata();
        String regionId = metadata.regionId == null ? "?" : metadata.regionId;
        String world = metadata.regionWorld == null ? "?" : metadata.regionWorld;
        String price = economy.format(listing.price(), listing.economyType(), definition);
        String economyLabel = definition == null ? listing.economyType().name() : definition.economy;
        Map<String, String> placeholders = RegionListingPresentation.listingPlaceholders(
                listing,
                price,
                economyLabel,
                worldGuardBridge
        );
        meta.displayName(messageService.component(
                viewerId,
                RegionMarketPresentation.listingTitleKey(settings),
                Map.of("region", placeholders.get("region"), "world", placeholders.get("world"))
        ));
        meta.lore(messageService.components(
                viewerId,
                RegionMarketPresentation.listingLoreKey(settings),
                placeholders
        ));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack placeholderIcon(AuctionSettings.RegionMarketSettings settings) {
        Material material = parseMaterial(settings == null ? "MAP" : settings.listIconMaterial, Material.MAP);
        return new ItemStack(material);
    }

    public String encodePlaceholder(AuctionSettings.RegionMarketSettings settings) {
        return ItemStackCodec.encode(placeholderIcon(settings));
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }
}
