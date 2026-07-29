package bm.b0b0b0.soulAuction.service.listing;

import bm.b0b0b0.soulAuction.api.event.AuctionListingSoldEvent;
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
import bm.b0b0b0.soulAuction.service.TaxPolicyResolver;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.Map;
import java.util.Optional;
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
    private final ListingLockRunner listingLocks;
    private final ListingSaleClaimer saleClaimer;
    private final java.util.function.Consumer<String> invalidateCacheForAuction;
    private final java.util.function.BiConsumer<String, AuctionListing> publishListingChange;
    private final MessageService messageService;

    public AuctionPurchaseService(
            AuctionRepository repository,
            Supplier<PluginConfig> configSupplier,
            AuctionEconomyService economy,
            TaxPolicyResolver taxPolicyResolver,
            AuctionRuntimeStorage runtimeStorage,
            AuctionExternalNotifier externalNotifier,
            ListingLockRunner listingLocks,
            ListingSaleClaimer saleClaimer,
            java.util.function.Consumer<String> invalidateCacheForAuction,
            java.util.function.BiConsumer<String, AuctionListing> publishListingChange,
            MessageService messageService
    ) {
        this.repository = repository;
        this.configSupplier = configSupplier;
        this.economy = economy;
        this.taxPolicyResolver = taxPolicyResolver;
        this.runtimeStorage = runtimeStorage;
        this.externalNotifier = externalNotifier;
        this.listingLocks = listingLocks;
        this.saleClaimer = saleClaimer;
        this.invalidateCacheForAuction = invalidateCacheForAuction;
        this.publishListingChange = publishListingChange;
        this.messageService = messageService;
    }

    public PurchaseResult purchase(Player buyer, long listingId, ListingDefinitionLookup definitions) {
        return listingLocks.withLock(listingId, () -> purchaseLocked(buyer, listingId, definitions));
    }

    private PurchaseResult purchaseLocked(Player buyer, long listingId, ListingDefinitionLookup definitions) {
        Optional<AuctionListing> claimed = saleClaimer.claim(listingId);
        if (claimed.isEmpty()) {
            return PurchaseResult.failure(PurchaseFailure.LISTING_UNAVAILABLE);
        }
        AuctionListing listing = claimed.get();
        AuctionSettings settings = configSupplier.get().auctionSettings();
        try {
            AuctionDefinitionSettings definition = definitions.find(listing.auctionId());
            if (definition == null) {
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.AUCTION_NOT_FOUND);
            }
            if (!definition.buyEnabled) {
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.BUY_DISABLED_IN_AUCTION);
            }
            if (!definitions.hasPermission(buyer, definition.buyPermission)) {
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.BUY_PERMISSION_DENIED);
            }
            if (!economy.isAvailable(listing.economyType(), definition)) {
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.ECONOMY_UNAVAILABLE);
            }
            if (!settings.limits.allowSelfBuy && listing.sellerId().equals(buyer.getUniqueId())) {
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.OWN_LISTING);
            }
            ItemStack item = ItemStackCodec.decode(listing.itemBase64());
            Player sellerOnline = Bukkit.getPlayer(listing.sellerId());
            TaxPolicyResolver.TaxAmounts taxes = taxPolicyResolver.resolve(
                    buyer,
                    sellerOnline,
                    definition,
                    settings,
                    listing.price(),
                    item
            );
            int charge = taxes.buyerCharge(listing.price());
            if (!economy.has(buyer.getUniqueId(), charge, listing.economyType(), definition)) {
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.NOT_ENOUGH_MONEY);
            }
            if (!economy.withdraw(buyer.getUniqueId(), charge, listing.economyType(), definition)) {
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.NOT_ENOUGH_MONEY);
            }
            Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                economy.deposit(buyer.getUniqueId(), charge, listing.economyType(), definition);
                saleClaimer.rollback(listing);
                return PurchaseResult.failure(PurchaseFailure.INVENTORY_FULL);
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
                    buyer.getUniqueId(),
                    listing.price(),
                    saleTax,
                    listing.economyType(),
                    buyTax
            );
            if (!sellerPaid) {
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
            if (publishListingChange != null) {
                publishListingChange.accept("REMOVE", listing);
            }
            Bukkit.getPluginManager().callEvent(new AuctionListingSoldEvent(listing, buyer, payout, saleTax, buyTax));
            maybeAnnounceSale(listing, buyer.getName(), definition);
            externalNotifier.sold(listing, buyer.getName(), buyer.getUniqueId(), economy.format(listing.price(), listing.economyType(), definition));
            return PurchaseResult.success(listing, sellerOnline, payout, saleTax, buyTax, charge);
        } catch (RuntimeException exception) {
            saleClaimer.rollback(listing);
            throw exception;
        }
    }

    private void maybeAnnounceSale(AuctionListing listing, String buyerName, AuctionDefinitionSettings definition) {
        AuctionSettings.AnnouncementSettings announcements = configSupplier.get().auctionSettings().announcements;
        if (!announcements.enabled || listing.price() < announcements.minPrice) {
            return;
        }
        Component message = messageService.component(
                "announce-sale",
                Map.of(
                        "buyer", buyerName,
                        "seller", listing.sellerName(),
                        "price", economy.format(listing.price(), listing.economyType(), definition),
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
