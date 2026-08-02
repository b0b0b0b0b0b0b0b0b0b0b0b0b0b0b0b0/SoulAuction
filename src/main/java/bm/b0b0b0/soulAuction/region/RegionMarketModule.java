package bm.b0b0b0.soulAuction.region;

import bm.b0b0b0.soulAuction.command.RegionMarketCommandHandler;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.region.RegionGuiListener;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.listener.RegionSellChatListener;
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
import bm.b0b0b0.soulAuction.service.region.RegionBrowseService;
import bm.b0b0b0.soulAuction.service.region.RegionDisplayItemFactory;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import bm.b0b0b0.soulAuction.service.region.RegionPurchaseService;
import bm.b0b0b0.soulAuction.service.region.RegionSellService;
import bm.b0b0b0.soulAuction.service.region.RegionSellSessionService;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegionMarketModule {

    private final RegionMarketService marketService;
    private final RegionMarketCommandHandler commandHandler;
    private final RegionGuiListener guiListener;
    private final RegionSellChatListener chatListener;

    private RegionMarketModule(
            RegionMarketService marketService,
            RegionMarketCommandHandler commandHandler,
            RegionGuiListener guiListener,
            RegionSellChatListener chatListener
    ) {
        this.marketService = marketService;
        this.commandHandler = commandHandler;
        this.guiListener = guiListener;
        this.chatListener = chatListener;
    }

    public static RegionMarketModule create(
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
            ListingSaleClaimer saleClaimer
    ) {
        WorldGuardBridge worldGuardBridge = new WorldGuardBridge();
        RegionBrowseService browseService = new RegionBrowseService(
                repository,
                listingCache,
                priorityResolver,
                configSupplier
        );
        RegionDisplayItemFactory displayItemFactory = new RegionDisplayItemFactory(messageService, economy);
        RegionSellService sellService = new RegionSellService(
                repository,
                configSupplier,
                economy,
                permissionLimitResolver,
                priceLimitResolver,
                redisSellGuard,
                runtimeStorage,
                sellPolicy,
                externalNotifier,
                worldGuardBridge,
                browseService,
                displayItemFactory,
                auctionService::invalidateListingCache
        );
        RegionPurchaseService purchaseService = new RegionPurchaseService(
                repository,
                configSupplier,
                economy,
                taxPolicyResolver,
                runtimeStorage,
                externalNotifier,
                listingLocks,
                saleClaimer,
                worldGuardBridge,
                auctionService::invalidateListingCache,
                auctionService::publishListingChange,
                messageService
        );
        RegionMarketService marketService = new RegionMarketService(
                configSupplier,
                auctionService::isLoaded,
                worldGuardBridge,
                browseService,
                sellService,
                purchaseService,
                new RegionSellSessionService(),
                displayItemFactory,
                auctionService
        );
        RegionMarketCommandHandler commandHandler = new RegionMarketCommandHandler(
                plugin,
                configSupplier,
                messageService,
                marketService
        );
        RegionGuiListener guiListener = new RegionGuiListener(plugin, configSupplier, marketService, messageService);
        RegionSellChatListener chatListener = new RegionSellChatListener(plugin, marketService, messageService);
        return new RegionMarketModule(marketService, commandHandler, guiListener, chatListener);
    }

    public RegionMarketService marketService() {
        return marketService;
    }

    public RegionMarketCommandHandler commandHandler() {
        return commandHandler;
    }

    public RegionGuiListener guiListener() {
        return guiListener;
    }

    public RegionSellChatListener chatListener() {
        return chatListener;
    }
}
