package bm.b0b0b0.soulAuction.region;

import bm.b0b0b0.soulAuction.command.RegionMarketCommandHandler;
import bm.b0b0b0.soulAuction.gui.region.RegionGuiListener;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.listener.RegionSellChatListener;
import bm.b0b0b0.soulAuction.service.region.RegionBrowseService;
import bm.b0b0b0.soulAuction.service.region.RegionDisplayItemFactory;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import bm.b0b0b0.soulAuction.service.region.RegionPurchaseService;
import bm.b0b0b0.soulAuction.service.region.RegionSellService;
import bm.b0b0b0.soulAuction.service.region.RegionSellSessionService;

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

    public static RegionMarketModule create(RegionMarketDependencies dependencies) {
        WorldGuardBridge worldGuardBridge = new WorldGuardBridge();
        RegionBrowseService browseService = new RegionBrowseService(
                dependencies.repository(),
                dependencies.listingCache(),
                dependencies.priorityResolver(),
                dependencies.configSupplier()
        );
        RegionDisplayItemFactory displayItemFactory = new RegionDisplayItemFactory(
                dependencies.messageService(),
                dependencies.economy()
        );
        var auctionService = dependencies.auctionService();
        RegionSellService sellService = new RegionSellService(
                dependencies.repository(),
                dependencies.configSupplier(),
                dependencies.economy(),
                dependencies.permissionLimitResolver(),
                dependencies.priceLimitResolver(),
                dependencies.redisSellGuard(),
                dependencies.runtimeStorage(),
                dependencies.sellPolicy(),
                dependencies.externalNotifier(),
                worldGuardBridge,
                browseService,
                displayItemFactory,
                auctionService::invalidateListingCache
        );
        RegionPurchaseService purchaseService = new RegionPurchaseService(
                dependencies.repository(),
                dependencies.configSupplier(),
                dependencies.economy(),
                dependencies.taxPolicyResolver(),
                dependencies.runtimeStorage(),
                dependencies.externalNotifier(),
                dependencies.listingLocks(),
                dependencies.listingSaleClaimer(),
                worldGuardBridge,
                auctionService::invalidateListingCache,
                auctionService::publishListingChange,
                dependencies.messageService()
        );
        RegionMarketService marketService = new RegionMarketService(
                dependencies.configSupplier(),
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
                dependencies.plugin(),
                dependencies.configSupplier(),
                dependencies.messageService(),
                marketService
        );
        RegionGuiListener guiListener = new RegionGuiListener(
                dependencies.plugin(),
                dependencies.configSupplier(),
                marketService,
                dependencies.messageService()
        );
        RegionSellChatListener chatListener = new RegionSellChatListener(
                dependencies.plugin(),
                marketService,
                dependencies.messageService()
        );
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
