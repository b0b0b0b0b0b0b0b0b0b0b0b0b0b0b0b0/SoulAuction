package bm.b0b0b0.soulAuction.region;

import bm.b0b0b0.soulAuction.command.RegionMarketCommandHandler;
import bm.b0b0b0.soulAuction.gui.region.RegionGuiListener;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.listener.RegionMarketCommandInterceptListener;
import bm.b0b0b0.soulAuction.listener.RegionSellChatListener;
import bm.b0b0b0.soulAuction.service.region.RegionBrowseService;
import bm.b0b0b0.soulAuction.service.region.RegionDisplayItemFactory;
import bm.b0b0b0.soulAuction.service.region.RegionListingGuard;
import bm.b0b0b0.soulAuction.service.region.RegionMarketService;
import bm.b0b0b0.soulAuction.service.region.RegionOwnerEditSessionService;
import bm.b0b0b0.soulAuction.service.region.RegionPurchaseService;
import bm.b0b0b0.soulAuction.service.region.RegionSellService;
import bm.b0b0b0.soulAuction.service.region.RegionSellSessionService;
import bm.b0b0b0.soulAuction.listener.RegionPreviewListener;
import bm.b0b0b0.soulAuction.service.region.RegionPreviewSessionService;
import bm.b0b0b0.soulAuction.service.region.RegionTeleportService;

public final class RegionMarketModule {

    private final RegionMarketService marketService;
    private final RegionMarketCommandHandler commandHandler;
    private final RegionGuiListener guiListener;
    private final RegionSellChatListener chatListener;
    private final RegionMarketCommandInterceptListener commandInterceptListener;
    private final RegionPreviewListener previewListener;
    private final RegionPreviewSessionService previewSessionService;

    private RegionMarketModule(
            RegionMarketService marketService,
            RegionMarketCommandHandler commandHandler,
            RegionGuiListener guiListener,
            RegionSellChatListener chatListener,
            RegionMarketCommandInterceptListener commandInterceptListener,
            RegionPreviewListener previewListener,
            RegionPreviewSessionService previewSessionService
    ) {
        this.marketService = marketService;
        this.commandHandler = commandHandler;
        this.guiListener = guiListener;
        this.chatListener = chatListener;
        this.commandInterceptListener = commandInterceptListener;
        this.previewListener = previewListener;
        this.previewSessionService = previewSessionService;
    }

    public static RegionMarketModule create(RegionMarketDependencies dependencies) {
        WorldGuardBridge worldGuardBridge = new WorldGuardBridge();
        var auctionService = dependencies.auctionService();
        RegionListingGuard listingGuard = new RegionListingGuard(
                dependencies.plugin(),
                worldGuardBridge,
                auctionService,
                dependencies.messageService()
        );
        RegionBrowseService browseService = new RegionBrowseService(
                dependencies.repository(),
                dependencies.listingCache(),
                dependencies.priorityResolver(),
                dependencies.configSupplier(),
                listingGuard
        );
        RegionDisplayItemFactory displayItemFactory = new RegionDisplayItemFactory(
                dependencies.messageService(),
                dependencies.economy(),
                worldGuardBridge
        );
        RegionPreviewSessionService previewSessionService = new RegionPreviewSessionService(
                dependencies.plugin(),
                dependencies.messageService()
        );
        RegionTeleportService teleportService = new RegionTeleportService(
                dependencies.plugin(),
                worldGuardBridge,
                dependencies.messageService(),
                previewSessionService
        );
        RegionOwnerEditSessionService ownerEditSessionService = new RegionOwnerEditSessionService();
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
                auctionService::invalidateListingCache,
                auctionService.announcementBroadcaster()
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
                listingGuard,
                auctionService::invalidateListingCache,
                auctionService::publishListingChange,
                auctionService.announcementBroadcaster()
        );
        RegionMarketService marketService = new RegionMarketService(
                dependencies.configSupplier(),
                auctionService::isLoaded,
                worldGuardBridge,
                browseService,
                sellService,
                purchaseService,
                new RegionSellSessionService(),
                ownerEditSessionService,
                displayItemFactory,
                teleportService,
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
        RegionMarketCommandInterceptListener commandInterceptListener = new RegionMarketCommandInterceptListener(
                () -> commandHandler
        );
        RegionPreviewListener previewListener = new RegionPreviewListener(previewSessionService);
        return new RegionMarketModule(
                marketService,
                commandHandler,
                guiListener,
                chatListener,
                commandInterceptListener,
                previewListener,
                previewSessionService
        );
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

    public RegionMarketCommandInterceptListener commandInterceptListener() {
        return commandInterceptListener;
    }

    public RegionPreviewListener previewListener() {
        return previewListener;
    }

    public RegionPreviewSessionService previewSessionService() {
        return previewSessionService;
    }
}
