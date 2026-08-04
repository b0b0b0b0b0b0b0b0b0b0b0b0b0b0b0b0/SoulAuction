package bm.b0b0b0.soulAuction.service.region;

import bm.b0b0b0.soulAuction.api.event.AuctionListingSoldEvent;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.integration.worldguard.WorldGuardBridge;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import bm.b0b0b0.soulAuction.model.region.RegionRef;
import bm.b0b0b0.soulAuction.model.result.PurchaseQuote;
import bm.b0b0b0.soulAuction.model.result.RegionPurchaseFailure;
import bm.b0b0b0.soulAuction.model.result.RegionPurchaseResult;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionAnnouncementBroadcaster;
import bm.b0b0b0.soulAuction.service.AuctionExternalNotifier;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.TaxPolicyResolver;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.service.listing.ListingLockRunner;
import bm.b0b0b0.soulAuction.service.listing.ListingSaleClaimer;
import bm.b0b0b0.soulAuction.region.RegionMarketPermissions;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.PermissionChecks;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RegionPurchaseService {

    private final AuctionRepository repository;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionEconomyService economy;
    private final TaxPolicyResolver taxPolicyResolver;
    private final AuctionRuntimeStorage runtimeStorage;
    private final AuctionExternalNotifier externalNotifier;
    private final ListingLockRunner listingLocks;
    private final ListingSaleClaimer saleClaimer;
    private final WorldGuardBridge worldGuardBridge;
    private final java.util.function.Consumer<String> invalidateCacheForAuction;
    private final java.util.function.BiConsumer<String, AuctionListing> publishListingChange;
    private final AuctionAnnouncementBroadcaster announcementBroadcaster;

    public RegionPurchaseService(
            AuctionRepository repository,
            Supplier<PluginConfig> configSupplier,
            AuctionEconomyService economy,
            TaxPolicyResolver taxPolicyResolver,
            AuctionRuntimeStorage runtimeStorage,
            AuctionExternalNotifier externalNotifier,
            ListingLockRunner listingLocks,
            ListingSaleClaimer saleClaimer,
            WorldGuardBridge worldGuardBridge,
            java.util.function.Consumer<String> invalidateCacheForAuction,
            java.util.function.BiConsumer<String, AuctionListing> publishListingChange,
            AuctionAnnouncementBroadcaster announcementBroadcaster
    ) {
        this.repository = repository;
        this.configSupplier = configSupplier;
        this.economy = economy;
        this.taxPolicyResolver = taxPolicyResolver;
        this.runtimeStorage = runtimeStorage;
        this.externalNotifier = externalNotifier;
        this.listingLocks = listingLocks;
        this.saleClaimer = saleClaimer;
        this.worldGuardBridge = worldGuardBridge;
        this.invalidateCacheForAuction = invalidateCacheForAuction;
        this.publishListingChange = publishListingChange;
        this.announcementBroadcaster = announcementBroadcaster;
    }

    public PurchaseQuote quote(Player buyer, long listingId) {
        AuctionListing listing = repository.findById(listingId);
        if (listing == null || !RegionListingHelper.isRegionListing(listing)) {
            return null;
        }
        AuctionDefinitionSettings definition = findDefinition(listing.auctionId());
        if (definition == null || buyer == null) {
            return null;
        }
        ItemStack placeholder = ItemStackCodec.decode(listing.itemBase64());
        Player sellerOnline = Bukkit.getPlayer(listing.sellerId());
        TaxPolicyResolver.TaxAmounts taxes = taxPolicyResolver.resolve(
                buyer,
                sellerOnline,
                definition,
                configSupplier.get().auctionSettings(),
                listing.price(),
                placeholder
        );
        return new PurchaseQuote(listing, taxes.buyerCharge(listing.price()), taxes.saleTax(), taxes.buyTax());
    }

    public RegionPurchaseResult purchase(Player buyer, long listingId, boolean loaded) {
        AuctionSettings settings = configSupplier.get().auctionSettings();
        AuctionSettings.RegionMarketSettings regionSettings = settings.regionMarket;
        if (regionSettings == null || !regionSettings.enabled) {
            return RegionPurchaseResult.failure(RegionPurchaseFailure.DISABLED);
        }
        if (!loaded) {
            return RegionPurchaseResult.failure(RegionPurchaseFailure.STORAGE_NOT_READY);
        }
        if (!PermissionChecks.has(buyer, RegionMarketPermissions.BUY)) {
            return RegionPurchaseResult.failure(RegionPurchaseFailure.REGION_BUY_PERMISSION_DENIED);
        }
        return listingLocks.withLock(listingId, () -> purchaseLocked(buyer, listingId, settings, regionSettings));
    }

    private RegionPurchaseResult purchaseLocked(
            Player buyer,
            long listingId,
            AuctionSettings settings,
            AuctionSettings.RegionMarketSettings regionSettings
    ) {
        Optional<AuctionListing> claimed = saleClaimer.claim(listingId);
        if (claimed.isEmpty()) {
            return RegionPurchaseResult.failure(RegionPurchaseFailure.LISTING_UNAVAILABLE);
        }
        AuctionListing listing = claimed.get();
        if (!RegionListingHelper.isRegionListing(listing)) {
            saleClaimer.rollback(listing);
            return RegionPurchaseResult.failure(RegionPurchaseFailure.NOT_REGION_LISTING);
        }
        RegionRef region = RegionListingHelper.regionRef(listing);
        try {
            AuctionDefinitionSettings definition = findDefinition(listing.auctionId());
            if (definition == null) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.AUCTION_NOT_FOUND);
            }
            if (!definition.buyEnabled) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.BUY_DISABLED_IN_AUCTION);
            }
            if (!PermissionChecks.has(buyer, definition.buyPermission)) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.AUCTION_BUY_PERMISSION_DENIED);
            }
            if (!economy.isAvailable(listing.economyType(), definition)) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.ECONOMY_UNAVAILABLE);
            }
            if (!settings.limits.allowSelfBuy && listing.sellerId().equals(buyer.getUniqueId())) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.OWN_LISTING);
            }
            if (!worldGuardBridge.regionExists(region) || !worldGuardBridge.isOwner(listing.sellerId(), region)) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.REGION_UNAVAILABLE);
            }
            ItemStack placeholder = ItemStackCodec.decode(listing.itemBase64());
            Player sellerOnline = Bukkit.getPlayer(listing.sellerId());
            TaxPolicyResolver.TaxAmounts taxes = taxPolicyResolver.resolve(
                    buyer,
                    sellerOnline,
                    definition,
                    settings,
                    listing.price(),
                    placeholder
            );
            int charge = taxes.buyerCharge(listing.price());
            if (!economy.has(buyer.getUniqueId(), charge, listing.economyType(), definition)) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.NOT_ENOUGH_MONEY);
            }
            if (!economy.withdraw(buyer.getUniqueId(), charge, listing.economyType(), definition)) {
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.NOT_ENOUGH_MONEY);
            }
            if (!worldGuardBridge.transferOwnership(region, listing.sellerId(), buyer.getUniqueId())) {
                economy.deposit(buyer.getUniqueId(), charge, listing.economyType(), definition);
                saleClaimer.rollback(listing);
                return RegionPurchaseResult.failure(RegionPurchaseFailure.TRANSFER_FAILED);
            }
            int saleTax = taxes.saleTax();
            int buyTax = taxes.buyTax();
            int payout = taxes.sellerPayout(listing.price());
            boolean sellerPaid = false;
            if (sellerOnline != null && settings.limits.autoClaimMoneyWhenOnline) {
                sellerPaid = economy.deposit(listing.sellerId(), payout, listing.economyType(), definition);
            }
            saleClaimer.commit(listing.listingId());
            runtimeStorage.addHistory(
                    "SOLD",
                    listing.auctionId(),
                    listing.listingId(),
                    listing.sellerId(),
                    listing.sellerName(),
                    buyer.getUniqueId(),
                    buyer.getName(),
                    listing.price(),
                    saleTax,
                    listing.economyType(),
                    buyTax
            );
            runtimeStorage.recordDealStats(listing.sellerId(), buyer.getUniqueId(), listing.economyType(), payout, charge);
            if (!sellerPaid) {
                PendingSaleNotification pending = new PendingSaleNotification(
                        listing.sellerId(),
                        listing.auctionId(),
                        payout,
                        saleTax,
                        listing.economyType()
                );
                if (repository.sharedPendingPayouts()) {
                    repository.storePendingPayout(pending);
                } else {
                    runtimeStorage.addPendingSaleNotification(pending);
                }
            }
            repository.flush();
            invalidateCacheForAuction.accept(listing.auctionId());
            if (publishListingChange != null) {
                publishListingChange.accept("REMOVE", listing);
            }
            Bukkit.getPluginManager().callEvent(new AuctionListingSoldEvent(listing, buyer, payout, saleTax, buyTax));
            announcementBroadcaster.maybeBroadcastRegionPurchase(listing, buyer.getName(), definition);
            externalNotifier.sold(
                    listing,
                    buyer.getName(),
                    buyer.getUniqueId(),
                    economy.format(listing.price(), listing.economyType(), definition)
            );
            return RegionPurchaseResult.success(listing, sellerOnline, payout, saleTax, buyTax, charge);
        } catch (RuntimeException exception) {
            saleClaimer.rollback(listing);
            throw exception;
        }
    }

    private AuctionDefinitionSettings findDefinition(String auctionId) {
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
}
