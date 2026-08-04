package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.service.region.RegionListingHelper;
import bm.b0b0b0.soulAuction.service.region.RegionMarketPresentation;
import bm.b0b0b0.soulAuction.util.ItemDisplayNames;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionAnnouncementBroadcaster {

    private final Supplier<PluginConfig> configSupplier;
    private final MessageService messageService;
    private final AuctionEconomyService economy;

    public AuctionAnnouncementBroadcaster(
            Supplier<PluginConfig> configSupplier,
            MessageService messageService,
            AuctionEconomyService economy
    ) {
        this.configSupplier = configSupplier;
        this.messageService = messageService;
        this.economy = economy;
    }

    public void maybeBroadcastItemPurchase(AuctionListing listing, String buyerName, AuctionDefinitionSettings definition) {
        AuctionSettings.ItemAnnouncementSettings settings = configSupplier.get().auctionSettings().announcements.items;
        if (!settings.broadcastPurchase || listing.price() < settings.minPurchasePrice) {
            return;
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        messageService.broadcast(
                "announce-item-purchase",
                Map.of(
                        "buyer", buyerName,
                        "seller", listing.sellerName(),
                        "item", ItemDisplayNames.plain(item),
                        "amount", String.valueOf(Math.max(1, item.getAmount())),
                        "price", formatPrice(listing, definition),
                        "auction", auctionLabel(definition)
                )
        );
    }

    public void maybeBroadcastItemListing(Player seller, AuctionListing listing, AuctionDefinitionSettings definition) {
        AuctionSettings.ItemAnnouncementSettings settings = configSupplier.get().auctionSettings().announcements.items;
        if (!settings.broadcastListing || listing.price() < settings.minListingPrice) {
            return;
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        Locale locale = messageService.javaLocale(seller.getUniqueId());
        messageService.broadcast(
                "announce-item-listing",
                Map.of(
                        "seller", seller.getName(),
                        "item", ItemDisplayNames.plain(item, locale),
                        "amount", String.valueOf(Math.max(1, item.getAmount())),
                        "price", formatPrice(listing, definition),
                        "auction", auctionLabel(definition),
                        "id", String.valueOf(listing.listingId())
                )
        );
    }

    public void maybeBroadcastRegionPurchase(AuctionListing listing, String buyerName, AuctionDefinitionSettings definition) {
        AuctionSettings.RegionAnnouncementSettings settings = configSupplier.get().auctionSettings().announcements.regions;
        if (!settings.broadcastPurchase || listing.price() < settings.minPurchasePrice) {
            return;
        }
        RegionRef region = RegionListingHelper.regionRef(listing);
        AuctionSettings.RegionMarketSettings regionMarket = configSupplier.get().auctionSettings().regionMarket;
        messageService.broadcast(
                RegionMarketPresentation.announcePurchaseKey(regionMarket),
                Map.of(
                        "buyer", buyerName,
                        "seller", listing.sellerName(),
                        "price", formatPrice(listing, definition),
                        "region", region.regionId(),
                        "world", region.worldName()
                )
        );
    }

    public void maybeBroadcastRegionListing(Player seller, AuctionListing listing, RegionRef region, AuctionDefinitionSettings definition) {
        AuctionSettings.RegionAnnouncementSettings settings = configSupplier.get().auctionSettings().announcements.regions;
        if (!settings.broadcastListing || listing.price() < settings.minListingPrice) {
            return;
        }
        AuctionSettings.RegionMarketSettings regionMarket = configSupplier.get().auctionSettings().regionMarket;
        messageService.broadcast(
                RegionMarketPresentation.announceListingKey(regionMarket),
                Map.of(
                        "seller", seller.getName(),
                        "price", formatPrice(listing, definition),
                        "region", region.regionId(),
                        "world", region.worldName(),
                        "auction", auctionLabel(definition),
                        "id", String.valueOf(listing.listingId())
                )
        );
    }

    private String formatPrice(AuctionListing listing, AuctionDefinitionSettings definition) {
        return economy.format(listing.price(), listing.economyType(), definition);
    }

    private static String auctionLabel(AuctionDefinitionSettings definition) {
        if (definition == null) {
            return "";
        }
        if (definition.displayName != null && !definition.displayName.isBlank()) {
            return definition.displayName;
        }
        return definition.id == null ? "" : definition.id;
    }
}
