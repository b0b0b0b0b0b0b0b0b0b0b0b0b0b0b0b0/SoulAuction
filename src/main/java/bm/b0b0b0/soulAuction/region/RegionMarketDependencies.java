package bm.b0b0b0.soulAuction.region;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionExternalNotifier;
import bm.b0b0b0.soulAuction.service.AuctionListingCache;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.PermissionLimitResolver;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.service.PriceLimitResolver;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import bm.b0b0b0.soulAuction.service.TaxPolicyResolver;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.service.listing.ListingLockRunner;
import bm.b0b0b0.soulAuction.service.listing.ListingSaleClaimer;
import bm.b0b0b0.soulAuction.service.policy.AuctionSellPolicy;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

public record RegionMarketDependencies(
        JavaPlugin plugin,
        Supplier<PluginConfig> configSupplier,
        MessageService messageService,
        AuctionService auctionService,
        AuctionRepository repository,
        AuctionListingCache listingCache,
        AuctionEconomyService economy,
        PermissionLimitResolver permissionLimitResolver,
        PermissionPriorityResolver priorityResolver,
        PriceLimitResolver priceLimitResolver,
        RedisSellGuard redisSellGuard,
        AuctionRuntimeStorage runtimeStorage,
        AuctionSellPolicy sellPolicy,
        AuctionExternalNotifier externalNotifier,
        TaxPolicyResolver taxPolicyResolver,
        ListingLockRunner listingLocks,
        ListingSaleClaimer listingSaleClaimer
) {
}
