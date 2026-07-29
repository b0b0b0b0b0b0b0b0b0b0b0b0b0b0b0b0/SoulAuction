package bm.b0b0b0.soulAuction.service.listing;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.result.SellFailure;
import bm.b0b0b0.soulAuction.model.result.SellResult;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionExternalNotifier;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.PermissionLimitResolver;
import bm.b0b0b0.soulAuction.service.PriceLimitResolver;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.service.policy.AuctionSellPolicy;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.ListingSearchText;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionListingCreator {

    private final AuctionRepository repository;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionEconomyService economy;
    private final PermissionLimitResolver permissionLimitResolver;
    private final PriceLimitResolver priceLimitResolver;
    private final RedisSellGuard redisSellGuard;
    private final AuctionRuntimeStorage runtimeStorage;
    private final AuctionSellPolicy sellPolicy;
    private final AuctionExternalNotifier externalNotifier;
    private final java.util.function.Consumer<String> invalidateCacheForAuction;
    private final LimitResolver limitResolver;

    public AuctionListingCreator(
            AuctionRepository repository,
            Supplier<PluginConfig> configSupplier,
            AuctionEconomyService economy,
            PermissionLimitResolver permissionLimitResolver,
            PriceLimitResolver priceLimitResolver,
            RedisSellGuard redisSellGuard,
            AuctionRuntimeStorage runtimeStorage,
            AuctionSellPolicy sellPolicy,
            AuctionExternalNotifier externalNotifier,
            java.util.function.Consumer<String> invalidateCacheForAuction,
            LimitResolver limitResolver
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
        this.invalidateCacheForAuction = invalidateCacheForAuction;
        this.limitResolver = limitResolver;
    }

    public SellResult create(Player seller, String auctionId, int price, ItemStack soldItem, DefinitionLookup definitions) {
        AuctionSettings settings = configSupplier.get().auctionSettings();
        if (!settings.limits.allowSelling) {
            return SellResult.failure(SellFailure.SELL_DISABLED);
        }
        if (!redisSellGuard.tryAcquireSellLock(seller.getUniqueId())) {
            return SellResult.failure(SellFailure.SELL_LOCK_FAILED);
        }
        AuctionDefinitionSettings definition = definitions.find(auctionId);
        if (definition == null) {
            return SellResult.failure(SellFailure.AUCTION_NOT_FOUND);
        }
        if (!definition.sellEnabled) {
            return SellResult.failure(SellFailure.SELL_DISABLED_IN_AUCTION);
        }
        if (!definitions.hasPermission(seller, definition.sellPermission)) {
            return SellResult.failure(SellFailure.SELL_PERMISSION_DENIED);
        }
        if (sellPolicy.isPlayerBlacklisted(seller.getUniqueId(), settings)) {
            return SellResult.failure(SellFailure.PLAYER_BLACKLISTED);
        }
        if (sellPolicy.isWorldSellBlocked(seller, settings)) {
            return SellResult.failure(SellFailure.WORLD_BLOCKED);
        }
        if (sellPolicy.isSellCooldownActive(seller.getUniqueId(), settings)) {
            return SellResult.failure(SellFailure.COOLDOWN);
        }
        if (!economy.isAvailable(AuctionEconomyType.fromString(definition.economy))) {
            return SellResult.failure(SellFailure.ECONOMY_UNAVAILABLE);
        }
        PriceLimitResolver.PriceBounds bounds = priceLimitResolver.resolve(seller, definition, settings.limits);
        if (!bounds.isValid(price)) {
            return SellResult.failure(price < bounds.minPrice() ? SellFailure.PRICE_TOO_LOW : SellFailure.PRICE_TOO_HIGH);
        }
        String normalizedAuctionId = definition.id.toLowerCase(Locale.ROOT);
        int auctionLimit = limitResolver.auctionLimit(seller, normalizedAuctionId);
        int globalLimit = limitResolver.globalLimit(seller);
        if (repository.countBySellerInAuction(seller.getUniqueId(), normalizedAuctionId) >= auctionLimit) {
            return SellResult.failure(SellFailure.AUCTION_LIMIT_REACHED);
        }
        if (repository.countBySeller(seller.getUniqueId()) >= globalLimit) {
            return SellResult.failure(SellFailure.GLOBAL_LIMIT_REACHED);
        }
        if (sellPolicy.isMaterialSellForbidden(soldItem.getType(), definition, settings)) {
            return SellResult.failure(settings.security.materialWhitelistMode ? SellFailure.WHITELIST_ITEM : SellFailure.BLOCKED_ITEM);
        }
        AuctionCategory category = AuctionCategory.fromMaterial(soldItem.getType());
        String searchText = ListingSearchText.fromItem(seller.getName(), soldItem);
        AuctionListing listing = repository.create(
                normalizedAuctionId,
                seller.getUniqueId(),
                seller.getName(),
                price,
                AuctionEconomyType.fromString(definition.economy),
                ItemStackCodec.encode(soldItem),
                category,
                searchText
        );
        repository.flush();
        runtimeStorage.recordSell(seller.getUniqueId());
        invalidateCacheForAuction.accept(normalizedAuctionId);
        externalNotifier.listingCreated(listing, soldItem);
        return SellResult.success(listing);
    }

    public interface DefinitionLookup {
        AuctionDefinitionSettings find(String auctionId);

        boolean hasPermission(Player player, String permission);
    }

    public interface LimitResolver {
        int auctionLimit(Player player, String auctionId);

        int globalLimit(Player player);
    }
}
