package bm.b0b0b0.soulAuction.service.listing;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import bm.b0b0b0.soulAuction.model.result.PurchaseFailure;
import bm.b0b0b0.soulAuction.model.result.PurchaseResult;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.AuctionExternalNotifier;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import bm.b0b0b0.soulAuction.service.TaxPolicyResolver;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionPurchaseService {

    private final AuctionRepository repository;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionEconomyService economy;
    private final TaxPolicyResolver taxPolicyResolver;
    private final AuctionRuntimeStorage runtimeStorage;
    private final AuctionExternalNotifier externalNotifier;
    private final RedisSellGuard redisSellGuard;
    private final ListingLockRunner listingLocks;
    private final java.util.function.Consumer<String> invalidateCacheForAuction;
    private final MessageService messageService;

    public AuctionPurchaseService(
            AuctionRepository repository,
            Supplier<PluginConfig> configSupplier,
            AuctionEconomyService economy,
            TaxPolicyResolver taxPolicyResolver,
            AuctionRuntimeStorage runtimeStorage,
            AuctionExternalNotifier externalNotifier,
            RedisSellGuard redisSellGuard,
            ListingLockRunner listingLocks,
            java.util.function.Consumer<String> invalidateCacheForAuction,
            MessageService messageService
    ) {
        this.repository = repository;
        this.configSupplier = configSupplier;
        this.economy = economy;
        this.taxPolicyResolver = taxPolicyResolver;
        this.runtimeStorage = runtimeStorage;
        this.externalNotifier = externalNotifier;
        this.redisSellGuard = redisSellGuard;
        this.listingLocks = listingLocks;
        this.invalidateCacheForAuction = invalidateCacheForAuction;
        this.messageService = messageService;
    }

    public PurchaseResult purchase(Player buyer, long listingId, ListingDefinitionLookup definitions) {
        if (!redisSellGuard.tryAcquireListingLock(listingId)) {
            return PurchaseResult.failure(PurchaseFailure.LISTING_UNAVAILABLE);
        }
        return listingLocks.withLock(listingId, () -> purchaseLocked(buyer, listingId, definitions));
    }

    private PurchaseResult purchaseLocked(Player buyer, long listingId, ListingDefinitionLookup definitions) {
        AuctionSettings settings = configSupplier.get().auctionSettings();
        try (RemovedListing hold = RemovedListing.take(repository, listingId)) {
            AuctionListing listing = hold.listing().orElse(null);
            if (listing == null) {
                return PurchaseResult.failure(PurchaseFailure.LISTING_UNAVAILABLE);
            }
            AuctionDefinitionSettings definition = definitions.find(listing.auctionId());
            if (definition == null) {
                return PurchaseResult.failure(PurchaseFailure.AUCTION_NOT_FOUND);
            }
            if (!definition.buyEnabled) {
                return PurchaseResult.failure(PurchaseFailure.BUY_DISABLED_IN_AUCTION);
            }
            if (!definitions.hasPermission(buyer, definition.buyPermission)) {
                return PurchaseResult.failure(PurchaseFailure.BUY_PERMISSION_DENIED);
            }
            if (!economy.isAvailable(listing.economyType())) {
                return PurchaseResult.failure(PurchaseFailure.ECONOMY_UNAVAILABLE);
            }
            if (!settings.limits.allowSelfBuy && listing.sellerId().equals(buyer.getUniqueId())) {
                return PurchaseResult.failure(PurchaseFailure.OWN_LISTING);
            }
            Player sellerOnline = Bukkit.getPlayer(listing.sellerId());
            TaxPolicyResolver.TaxAmounts taxes = taxPolicyResolver.resolve(buyer, sellerOnline, definition, listing.price());
            int charge = taxes.buyerCharge(listing.price());
            if (!economy.has(buyer.getUniqueId(), charge, listing.economyType())) {
                return PurchaseResult.failure(PurchaseFailure.NOT_ENOUGH_MONEY);
            }
            if (!economy.withdraw(buyer.getUniqueId(), charge, listing.economyType())) {
                return PurchaseResult.failure(PurchaseFailure.NOT_ENOUGH_MONEY);
            }
            ItemStack item = ItemStackCodec.decode(listing.itemBase64());
            Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                economy.deposit(buyer.getUniqueId(), charge, listing.economyType());
                return PurchaseResult.failure(PurchaseFailure.INVENTORY_FULL);
            }
            int saleTax = taxes.saleTax();
            int buyTax = taxes.buyTax();
            int payout = taxes.sellerPayout(listing.price());
            boolean sellerPaid = economy.deposit(listing.sellerId(), payout, listing.economyType());
            hold.commit();
            runtimeStorage.addHistory(
                    "SOLD",
                    listing.auctionId(),
                    listing.listingId(),
                    listing.sellerId(),
                    buyer.getUniqueId(),
                    listing.price(),
                    saleTax,
                    listing.economyType(),
                    buyTax
            );
            if (!sellerPaid || sellerOnline == null) {
                runtimeStorage.addPendingSaleNotification(new PendingSaleNotification(
                        listing.sellerId(),
                        listing.auctionId(),
                        payout,
                        saleTax,
                        listing.economyType()
                ));
            }
            repository.flush();
            invalidateCacheForAuction.accept(listing.auctionId());
            maybeAnnounceSale(listing, buyer.getName(), definitions);
            externalNotifier.sold(listing, buyer.getName(), buyer.getUniqueId(), economy.format(listing.price(), listing.economyType()));
            return PurchaseResult.success(listing, sellerOnline, payout, saleTax, buyTax, charge);
        }
    }

    private void maybeAnnounceSale(AuctionListing listing, String buyerName, ListingDefinitionLookup definitions) {
        AuctionSettings.AnnouncementSettings announcements = configSupplier.get().auctionSettings().announcements;
        if (!announcements.enabled || listing.price() < announcements.minPrice) {
            return;
        }
        Component message = messageService.component(
                "announce-sale",
                Map.of(
                        "buyer", buyerName,
                        "seller", listing.sellerName(),
                        "price", economy.format(listing.price(), listing.economyType()),
                        "auction", listing.auctionId()
                )
        );
        Bukkit.getServer().broadcast(message);
    }

    public interface ListingDefinitionLookup {

        AuctionDefinitionSettings find(String auctionId);

        boolean hasPermission(Player player, String permission);
    }
}
