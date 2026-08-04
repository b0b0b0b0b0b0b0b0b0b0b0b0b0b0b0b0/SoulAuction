package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.model.result.CancelFailure;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.model.result.EditDescriptionResult;
import bm.b0b0b0.soulAuction.model.result.EditPriceResult;
import bm.b0b0b0.soulAuction.model.result.PurchaseQuote;
import bm.b0b0b0.soulAuction.model.result.RegionPurchaseResult;
import bm.b0b0b0.soulAuction.model.result.RegionSellResult;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.region.RegionBrowseService.BrowsePage;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

public final class RegionMarketService {

    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<Boolean> loadedSupplier;
    private final WorldGuardBridge worldGuardBridge;
    private final RegionBrowseService browseService;
    private final RegionSellService sellService;
    private final RegionPurchaseService purchaseService;
    private final RegionSellSessionService sessionService;
    private final RegionOwnerEditSessionService ownerEditSessionService;
    private final RegionDisplayItemFactory displayItemFactory;
    private final RegionTeleportService teleportService;
    private final AuctionService auctionService;

    public RegionMarketService(
            Supplier<PluginConfig> configSupplier,
            Supplier<Boolean> loadedSupplier,
            WorldGuardBridge worldGuardBridge,
            RegionBrowseService browseService,
            RegionSellService sellService,
            RegionPurchaseService purchaseService,
            RegionSellSessionService sessionService,
            RegionOwnerEditSessionService ownerEditSessionService,
            RegionDisplayItemFactory displayItemFactory,
            RegionTeleportService teleportService,
            AuctionService auctionService
    ) {
        this.configSupplier = configSupplier;
        this.loadedSupplier = loadedSupplier;
        this.worldGuardBridge = worldGuardBridge;
        this.browseService = browseService;
        this.sellService = sellService;
        this.purchaseService = purchaseService;
        this.sessionService = sessionService;
        this.ownerEditSessionService = ownerEditSessionService;
        this.displayItemFactory = displayItemFactory;
        this.teleportService = teleportService;
        this.auctionService = auctionService;
    }

    public boolean isOperational() {
        AuctionSettings.RegionMarketSettings settings = settings();
        return settings != null && settings.enabled && worldGuardBridge.available();
    }

    public AuctionSettings.RegionMarketSettings settings() {
        AuctionSettings settings = configSupplier.get().auctionSettings();
        return settings == null ? null : settings.regionMarket;
    }

    public WorldGuardBridge worldGuardBridge() {
        return worldGuardBridge;
    }

    public RegionBrowseService browseService() {
        return browseService;
    }

    public RegionSellSessionService sessionService() {
        return sessionService;
    }

    public RegionOwnerEditSessionService ownerEditSessionService() {
        return ownerEditSessionService;
    }

    public RegionDisplayItemFactory displayItemFactory() {
        return displayItemFactory;
    }

    public AuctionListing listingById(long listingId) {
        return auctionService.listingById(listingId);
    }

    public int globalMaxPrice() {
        return auctionService.globalMaxPrice();
    }

    public int maxPrice(Player player, String auctionId) {
        return auctionService.maxPrice(player, auctionId);
    }

    public EditPriceResult editListingPrice(Player seller, long listingId, int newPrice) {
        return auctionService.editListingPrice(seller, listingId, newPrice);
    }

    public EditDescriptionResult editListingDescription(Player seller, long listingId, String description) {
        AuctionSettings.RegionMarketSettings regionSettings = settings();
        int maxLength = regionSettings == null ? 200 : regionSettings.maxDescriptionLength;
        return auctionService.editRegionListingDescription(seller, listingId, description, maxLength);
    }

    public BrowsePage browsePage(AuctionSort sort, int page, int pageSize, UUID sellerFilter) {
        return browseService.browsePage(sort, page, pageSize, sellerFilter);
    }

    public AuctionDefinitionSettings findDefinition(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return null;
        }
        for (AuctionDefinitionSettings definition : configSupplier.get().auctionDefinitions()) {
            if (definition != null && definition.id != null && definition.id.equalsIgnoreCase(auctionId)) {
                return definition;
            }
        }
        return null;
    }

    public List<AuctionDefinitionSettings> sellableAuctions(Player seller) {
        return browseService.sellableAuctions(seller.getUniqueId(), settings());
    }

    public RegionSellResult sell(Player seller, RegionRef region, String auctionId, int price) {
        return sellService.sell(seller, region, auctionId, price, loadedSupplier.get());
    }

    public RegionSellResult sell(Player seller, RegionRef region, String auctionId, int price, String description) {
        return sellService.sell(seller, region, auctionId, price, description, loadedSupplier.get());
    }

    public RegionPurchaseResult purchase(Player buyer, long listingId) {
        return purchaseService.purchase(buyer, listingId, loadedSupplier.get());
    }

    public PurchaseQuote quotePurchase(Player buyer, long listingId) {
        return purchaseService.quote(buyer, listingId);
    }

    public String formatPrice(int amount, String auctionId, UUID viewerId) {
        return auctionService.formatPrice(amount, auctionId, viewerId);
    }

    public boolean previewTeleport(Player player, long listingId) {
        AuctionListing listing = listingById(listingId);
        return teleportService.teleportToListing(player, listing, settings());
    }

    public boolean cancelPreview(Player player) {
        if (player == null) {
            return false;
        }
        return teleportService.previewSessions().cancel(player);
    }

    public CancelResult cancelListing(Player seller, long listingId, boolean canCancelAny) {
        AuctionListing listing = listingById(listingId);
        if (listing == null || !RegionListingHelper.isRegionListing(listing)) {
            return CancelResult.failure(CancelFailure.NOT_FOUND);
        }
        return auctionService.cancelRegionListing(seller, listingId, canCancelAny);
    }

    public RegionRef resolveSellerRegion(Player player, String input) {
        return RegionRef.resolveForSeller(input, player, worldGuardBridge);
    }

    public List<String> tabCompleteOwnedRegions(Player player, String partial) {
        return worldGuardBridge.tabCompleteOwnedRegions(player, partial, RegionMarketPresentation.hideWorldName(settings()));
    }

    public List<String> tabCompleteSellableAuctions(Player player, String partial) {
        String needle = partial == null ? "" : partial.toLowerCase();
        return sellableAuctions(player).stream()
                .map(definition -> definition.id)
                .filter(id -> needle.isEmpty() || id.toLowerCase().startsWith(needle))
                .toList();
    }
}
