package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.ListingMetadata;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.model.result.RegionSellFailure;
import bm.b0b0b0.soulAuction.model.result.RegionSellResult;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionAnnouncementBroadcaster;
import bm.b0b0b0.soulAuction.service.AuctionExternalNotifier;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.PermissionLimitResolver;
import bm.b0b0b0.soulAuction.service.PriceLimitResolver;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.service.policy.AuctionSellPolicy;
import bm.b0b0b0.soulAuction.region.RegionMarketPermissions;
import bm.b0b0b0.soulAuction.util.PermissionChecks;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RegionSellService {

    private final AuctionRepository repository;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionEconomyService economy;
    private final PermissionLimitResolver permissionLimitResolver;
    private final PriceLimitResolver priceLimitResolver;
    private final RedisSellGuard redisSellGuard;
    private final AuctionRuntimeStorage runtimeStorage;
    private final AuctionSellPolicy sellPolicy;
    private final AuctionExternalNotifier externalNotifier;
    private final WorldGuardBridge worldGuardBridge;
    private final RegionBrowseService browseService;
    private final RegionDisplayItemFactory displayItemFactory;
    private final java.util.function.Consumer<String> invalidateCacheForAuction;
    private final AuctionAnnouncementBroadcaster announcementBroadcaster;

    public RegionSellService(
            AuctionRepository repository,
            Supplier<PluginConfig> configSupplier,
            AuctionEconomyService economy,
            PermissionLimitResolver permissionLimitResolver,
            PriceLimitResolver priceLimitResolver,
            RedisSellGuard redisSellGuard,
            AuctionRuntimeStorage runtimeStorage,
            AuctionSellPolicy sellPolicy,
            AuctionExternalNotifier externalNotifier,
            WorldGuardBridge worldGuardBridge,
            RegionBrowseService browseService,
            RegionDisplayItemFactory displayItemFactory,
            java.util.function.Consumer<String> invalidateCacheForAuction,
            AuctionAnnouncementBroadcaster announcementBroadcaster
    ) {
        this.repository = repository;
        this.configSupplier = configSupplier;
        this.economy = economy;
        this.permissionLimitResolver = permissionLimitResolver;
        this.priceLimitResolver = priceLimitResolver;
        this.redisSellGuard = redisSellGuard;
        this.runtimeStorage = runtimeStorage;
        this.sellPolicy = sellPolicy;
        this.externalNotifier = externalNotifier;
        this.worldGuardBridge = worldGuardBridge;
        this.browseService = browseService;
        this.displayItemFactory = displayItemFactory;
        this.invalidateCacheForAuction = invalidateCacheForAuction;
        this.announcementBroadcaster = announcementBroadcaster;
    }

    public RegionSellResult sell(Player seller, RegionRef region, String auctionId, int price, boolean loaded) {
        return sell(seller, region, auctionId, price, "", loaded);
    }

    public RegionSellResult sell(Player seller, RegionRef region, String auctionId, int price, String description, boolean loaded) {
        AuctionSettings settings = configSupplier.get().auctionSettings();
        AuctionSettings.RegionMarketSettings regionSettings = settings.regionMarket;
        if (regionSettings == null || !regionSettings.enabled) {
            return RegionSellResult.failure(RegionSellFailure.DISABLED);
        }
        if (!worldGuardBridge.available()) {
            return RegionSellResult.failure(RegionSellFailure.WORLDGUARD_UNAVAILABLE);
        }
        if (!loaded) {
            return RegionSellResult.failure(RegionSellFailure.STORAGE_NOT_READY);
        }
        if (!PermissionChecks.has(seller, RegionMarketPermissions.SELL)) {
            return RegionSellResult.failure(RegionSellFailure.NO_PERMISSION);
        }
        if (!redisSellGuard.tryAcquireSellLock(seller.getUniqueId())) {
            return RegionSellResult.failure(RegionSellFailure.SELL_LOCK_FAILED);
        }
        try {
            return sellLocked(seller, region, auctionId, price, description, settings, regionSettings);
        } finally {
            redisSellGuard.releaseSellLock(seller.getUniqueId());
        }
    }

    private RegionSellResult sellLocked(
            Player seller,
            RegionRef region,
            String auctionId,
            int price,
            String description,
            AuctionSettings settings,
            AuctionSettings.RegionMarketSettings regionSettings
    ) {
        if (region == null) {
            return RegionSellResult.failure(RegionSellFailure.REGION_NOT_FOUND);
        }
        if (!worldGuardBridge.regionExists(region)) {
            return RegionSellResult.failure(RegionSellFailure.REGION_NOT_FOUND);
        }
        if (!worldGuardBridge.isOwner(seller.getUniqueId(), region)) {
            return RegionSellResult.failure(RegionSellFailure.NOT_OWNER);
        }
        if (browseService.isRegionListed(region)) {
            return RegionSellResult.failure(RegionSellFailure.ALREADY_LISTED);
        }
        if (!settings.limits.allowSelling) {
            return RegionSellResult.failure(RegionSellFailure.SELL_DISABLED_IN_AUCTION);
        }
        AuctionDefinitionSettings definition = findDefinition(auctionId, regionSettings);
        if (definition == null) {
            return RegionSellResult.failure(RegionSellFailure.AUCTION_NOT_FOUND);
        }
        if (!definition.sellEnabled) {
            return RegionSellResult.failure(RegionSellFailure.SELL_DISABLED_IN_AUCTION);
        }
        if (!PermissionChecks.has(seller, definition.sellPermission)) {
            return RegionSellResult.failure(RegionSellFailure.SELL_PERMISSION_DENIED);
        }
        if (sellPolicy.isPlayerBlacklisted(seller.getUniqueId(), settings)) {
            return RegionSellResult.failure(RegionSellFailure.PLAYER_BLACKLISTED);
        }
        if (sellPolicy.isWorldSellBlocked(seller, settings)) {
            return RegionSellResult.failure(RegionSellFailure.WORLD_BLOCKED);
        }
        if (sellPolicy.isSellCooldownActive(seller.getUniqueId(), settings)) {
            return RegionSellResult.failure(RegionSellFailure.COOLDOWN);
        }
        AuctionEconomyType economyType = AuctionEconomyType.fromString(definition.economy);
        if (!economy.isAvailable(economyType, definition)) {
            return RegionSellResult.failure(RegionSellFailure.ECONOMY_UNAVAILABLE);
        }
        if (price < 1) {
            return RegionSellResult.failure(RegionSellFailure.INVALID_PRICE);
        }
        ItemStack placeholder = displayItemFactory.placeholderIcon(regionSettings);
        PriceLimitResolver.PriceBounds bounds = priceLimitResolver.resolve(seller, definition, settings, placeholder);
        if (!bounds.isValid(price)) {
            return RegionSellResult.failure(price < bounds.minPrice() ? RegionSellFailure.PRICE_TOO_LOW : RegionSellFailure.PRICE_TOO_HIGH);
        }
        String normalizedAuctionId = definition.id.toLowerCase(Locale.ROOT);
        int regionLimit = regionSettings.maxListingsPerPlayer;
        if (regionLimit > 0 && browseService.countBySeller(seller.getUniqueId()) >= regionLimit) {
            return RegionSellResult.failure(RegionSellFailure.AUCTION_LIMIT_REACHED);
        }
        int auctionLimit = permissionLimitResolver.resolveAuctionLimit(
                seller,
                normalizedAuctionId,
                settings.limits.defaultMaxActiveListingsPerAuction
        );
        int globalLimit = permissionLimitResolver.resolveGlobalLimit(
                seller,
                settings.limits.defaultMaxActiveListingsGlobal
        );
        if (repository.countBySellerInAuction(seller.getUniqueId(), normalizedAuctionId) >= auctionLimit) {
            return RegionSellResult.failure(RegionSellFailure.AUCTION_LIMIT_REACHED);
        }
        if (repository.countBySeller(seller.getUniqueId()) >= globalLimit) {
            return RegionSellResult.failure(RegionSellFailure.GLOBAL_LIMIT_REACHED);
        }
        ListingMetadata metadata = RegionListingHelper.regionMetadata(
                region,
                settings.network == null ? "" : settings.network.serverId,
                worldGuardBridge.regionInfo(region),
                sanitizeDescription(description, regionSettings)
        );
        String auctionLabel = definition.displayName == null || definition.displayName.isBlank()
                ? definition.id
                : definition.displayName;
        String searchText = RegionListingPresentation.buildSearchText(region, seller.getName(), auctionLabel, metadata.regionDescription);
        AuctionListing listing = repository.create(
                normalizedAuctionId,
                seller.getUniqueId(),
                seller.getName(),
                price,
                economyType,
                displayItemFactory.encodePlaceholder(regionSettings),
                AuctionCategory.OTHER,
                searchText,
                metadata.toJson()
        );
        if (listing == null) {
            return RegionSellResult.failure(RegionSellFailure.LISTING_ID_ALLOCATION_FAILED);
        }
        repository.flush();
        runtimeStorage.recordSell(seller.getUniqueId());
        invalidateCacheForAuction.accept(normalizedAuctionId);
        externalNotifier.listingCreated(listing, placeholder);
        announcementBroadcaster.maybeBroadcastRegionListing(seller, listing, region, definition);
        return RegionSellResult.success(listing);
    }

    private String sanitizeDescription(String description, AuctionSettings.RegionMarketSettings regionSettings) {
        if (description == null || description.isBlank()) {
            return "";
        }
        int max = regionSettings == null ? 200 : Math.max(1, regionSettings.maxDescriptionLength);
        String trimmed = description.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, max);
    }

    private AuctionDefinitionSettings findDefinition(String auctionId, AuctionSettings.RegionMarketSettings regionSettings) {
        if (auctionId == null || auctionId.isBlank()) {
            return null;
        }
        String normalized = auctionId.toLowerCase(Locale.ROOT);
        List<String> allowed = normalizedAllowedIds(regionSettings);
        for (AuctionDefinitionSettings definition : configSupplier.get().auctionDefinitions()) {
            if (definition == null || definition.id == null) {
                continue;
            }
            if (!definition.id.equalsIgnoreCase(normalized)) {
                continue;
            }
            if (!allowed.isEmpty() && !allowed.contains(normalized)) {
                return null;
            }
            return definition;
        }
        return null;
    }

    private List<String> normalizedAllowedIds(AuctionSettings.RegionMarketSettings settings) {
        if (settings == null || settings.allowedAuctionIds == null || settings.allowedAuctionIds.isEmpty()) {
            return List.of();
        }
        return settings.allowedAuctionIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> id.toLowerCase(Locale.ROOT))
                .toList();
    }
}

