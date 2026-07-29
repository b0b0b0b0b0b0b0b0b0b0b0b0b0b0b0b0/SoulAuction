package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.model.AuditLogEntry;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import bm.b0b0b0.soulAuction.model.result.CancelFailure;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.model.result.ClaimResult;
import bm.b0b0b0.soulAuction.model.result.EditPriceResult;
import bm.b0b0b0.soulAuction.model.result.PurchaseQuote;
import bm.b0b0b0.soulAuction.model.result.PurchaseResult;
import bm.b0b0b0.soulAuction.model.result.SellFailure;
import bm.b0b0b0.soulAuction.model.result.SellResult;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowseFilterState;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowsePage;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.service.listing.AuctionListingCreator;
import bm.b0b0b0.soulAuction.service.listing.AuctionPurchaseService;
import bm.b0b0b0.soulAuction.service.listing.ListingLockRunner;
import bm.b0b0b0.soulAuction.service.listing.RemovedListing;
import bm.b0b0b0.soulAuction.service.policy.AuctionSellPolicy;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class AuctionService {

    private final AuctionRepository repository;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionEconomyService economy;
    private final PermissionLimitResolver permissionLimitResolver;
    private final RedisSellGuard redisSellGuard;
    private final AuctionRuntimeStorage runtimeStorage;
    private final TaxPolicyResolver taxPolicyResolver;
    private final PriceLimitResolver priceLimitResolver;
    private final AuctionListingCache listingCache;
    private final AuctionListingCreator listingCreator;
    private final AuctionPurchaseService purchaseService;
    private final AuctionBrowseService browseService;
    private final ListingLockRunner listingLocks;
    private final AuctionExternalNotifier externalNotifier;
    private final ConcurrentHashMap<UUID, BrowsePreferences> browsePreferences;
    private final ConcurrentHashMap<UUID, BrowseFilterState> browseFilters;
    private final ConcurrentHashMap<UUID, PendingChatSearch> pendingChatSearch;
    private final ConcurrentHashMap<UUID, Object> playerSellLocks;

    private final LimitResolverImpl limitResolver = new LimitResolverImpl();

    public AuctionService(
            AuctionRepository repository,
            Supplier<PluginConfig> configSupplier,
            EconomyBridge vaultBridge,
            PlayerPointsBridge playerPointsBridge,
            ExperienceEconomyBridge experienceBridge,
            CoinsEngineBridge coinsEngineBridge,
            PermissionLimitResolver permissionLimitResolver,
            PermissionPriorityResolver priorityResolver,
            RedisSellGuard redisSellGuard,
            AuctionRuntimeStorage runtimeStorage,
            TaxPolicyResolver taxPolicyResolver,
            PriceLimitResolver priceLimitResolver,
            AuctionExternalNotifier externalNotifier,
            MessageService messageService,
            AuctionListingCache listingCache
    ) {
        this.repository = repository;
        this.configSupplier = configSupplier;
        this.economy = new AuctionEconomyService(vaultBridge, playerPointsBridge, experienceBridge, coinsEngineBridge);
        this.permissionLimitResolver = permissionLimitResolver;
        this.redisSellGuard = redisSellGuard;
        this.runtimeStorage = runtimeStorage;
        this.taxPolicyResolver = taxPolicyResolver;
        this.priceLimitResolver = priceLimitResolver;
        this.listingCache = listingCache;
        this.externalNotifier = externalNotifier;
        this.listingLocks = new ListingLockRunner();
        AuctionSellPolicy sellPolicy = new AuctionSellPolicy(runtimeStorage);
        java.util.function.Consumer<String> invalidate = this::invalidateListingCache;
        this.listingCreator = new AuctionListingCreator(
                repository,
                configSupplier,
                economy,
                permissionLimitResolver,
                priceLimitResolver,
                redisSellGuard,
                runtimeStorage,
                sellPolicy,
                externalNotifier,
                invalidate,
                limitResolver
        );
        this.purchaseService = new AuctionPurchaseService(
                repository,
                configSupplier,
                economy,
                taxPolicyResolver,
                runtimeStorage,
                externalNotifier,
                redisSellGuard,
                listingLocks,
                invalidate,
                messageService
        );
        this.browseService = new AuctionBrowseService(repository, listingCache, runtimeStorage, priorityResolver);
        this.browsePreferences = new ConcurrentHashMap<>();
        this.browseFilters = new ConcurrentHashMap<>();
        this.pendingChatSearch = new ConcurrentHashMap<>();
        this.playerSellLocks = new ConcurrentHashMap<>();
    }

    public record BrowsePreferences(String auctionId, int page, String searchQuery) {
    }

    public record PendingChatSearch(String auctionId) {
    }

    public BrowseFilterState browseFilterState(UUID playerId) {
        return browseFilters.getOrDefault(playerId, BrowseFilterState.empty());
    }

    public void setBrowseFilterState(UUID playerId, BrowseFilterState state) {
        if (state == null) {
            browseFilters.remove(playerId);
            return;
        }
        browseFilters.put(playerId, state);
    }

    public void beginPendingChatSearch(UUID playerId, String auctionId) {
        pendingChatSearch.put(playerId, new PendingChatSearch(auctionId.toLowerCase(Locale.ROOT)));
    }

    public Optional<PendingChatSearch> consumePendingChatSearch(UUID playerId) {
        return Optional.ofNullable(pendingChatSearch.remove(playerId));
    }

    public boolean toggleFavoriteSeller(UUID viewerId, UUID sellerId) {
        return runtimeStorage.toggleFavoriteSeller(viewerId, sellerId);
    }

    public boolean isFavoriteSeller(UUID viewerId, UUID sellerId) {
        return runtimeStorage.isFavoriteSeller(viewerId, sellerId);
    }

    public void invalidateListingCache(String auctionId) {
        listingCache.invalidate(auctionId);
        redisSellGuard.publishCacheInvalidate(auctionId);
    }

    public void attachCacheSubscriber() {
        redisSellGuard.startCacheSubscriber(payload -> {
            if (payload == null || payload.equals("*")) {
                listingCache.invalidateAll();
                return;
            }
            listingCache.invalidate(payload);
        });
    }

    public void setBrowsePreferences(UUID playerId, BrowsePreferences preferences) {
        if (preferences == null) {
            browsePreferences.remove(playerId);
            return;
        }
        browsePreferences.put(playerId, preferences);
    }

    public Optional<BrowsePreferences> consumeBrowsePreferences(UUID playerId) {
        return Optional.ofNullable(browsePreferences.remove(playerId));
    }

    public CompletableFuture<Void> load() {
        return repository.load().thenCompose(unused -> runtimeStorage.load());
    }

    public CompletableFuture<Void> flush() {
        return repository.flush().thenCompose(unused -> runtimeStorage.flush());
    }

    public CompletableFuture<Void> close() {
        return flush().thenCompose(unused -> repository.close()).thenCompose(unused -> runtimeStorage.close());
    }

    public SellResult createListing(Player seller, String auctionId, int price) {
        ItemStack itemInHand = seller.getInventory().getItemInMainHand();
        if (itemInHand.isEmpty()) {
            return SellResult.failure(SellFailure.EMPTY_HAND);
        }
        ItemStack soldItem = itemInHand.clone();
        Object sellLock = playerSellLocks.computeIfAbsent(seller.getUniqueId(), ignored -> new Object());
        synchronized (sellLock) {
            SellResult result = listingCreator.create(seller, auctionId, price, soldItem, definitionLookup());
            if (result.success()) {
                seller.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
            return result;
        }
    }

    public SellResult createListingFromItem(Player seller, String auctionId, int price, ItemStack item) {
        return createListingFromItem(seller, auctionId, price, item, item == null ? 0 : item.getAmount());
    }

    public SellResult createListingFromItem(Player seller, String auctionId, int price, ItemStack item, int amount) {
        if (item == null || item.isEmpty()) {
            return SellResult.failure(SellFailure.EMPTY_HAND);
        }
        int sellAmount = Math.min(Math.max(1, amount), item.getAmount());
        ItemStack soldItem = item.clone();
        soldItem.setAmount(sellAmount);
        Object sellLock = playerSellLocks.computeIfAbsent(seller.getUniqueId(), ignored -> new Object());
        synchronized (sellLock) {
            return listingCreator.create(seller, auctionId, price, soldItem, definitionLookup());
        }
    }

    public PurchaseResult purchase(Player buyer, long listingId) {
        return purchaseService.purchase(buyer, listingId, purchaseDefinitionLookup());
    }

    public PurchaseQuote quotePurchase(Player buyer, long listingId) {
        AuctionListing listing = repository.findById(listingId);
        if (listing == null) {
            return null;
        }
        AuctionDefinitionSettings definition = findAuction(listing.auctionId(), configSupplier.get());
        if (definition == null) {
            return null;
        }
        Player sellerOnline = Bukkit.getPlayer(listing.sellerId());
        TaxPolicyResolver.TaxAmounts taxes = taxPolicyResolver.resolve(buyer, sellerOnline, definition, listing.price());
        return new PurchaseQuote(listing, taxes.buyerCharge(listing.price()), taxes.saleTax(), taxes.buyTax());
    }

    public int expireListings() {
        int expired = 0;
        long now = System.currentTimeMillis();
        List<AuctionListing> snapshot = repository.listAll();
        for (AuctionListing listing : snapshot) {
            AuctionDefinitionSettings definition = findAuction(listing.auctionId(), configSupplier.get());
            if (definition == null) {
                continue;
            }
            long ttlMillis = Math.max(1L, definition.listingTtlSeconds) * 1000L;
            if (now - listing.createdAtEpochMillis() < ttlMillis) {
                continue;
            }
            boolean processed = listingLocks.withLock(listing.listingId(), () -> expireOne(listing.listingId()));
            if (processed) {
                expired++;
            }
        }
        if (expired > 0) {
            repository.flush();
            listingCache.invalidateAll();
        }
        return expired;
    }

    private boolean expireOne(long listingId) {
        try (RemovedListing hold = RemovedListing.take(repository, listingId)) {
            AuctionListing removed = hold.listing().orElse(null);
            if (removed == null) {
                return false;
            }
            runtimeStorage.addClaim(
                    removed.sellerId(),
                    removed.auctionId(),
                    removed.listingId(),
                    removed.itemBase64(),
                    "EXPIRED"
            );
            hold.commit();
            runtimeStorage.addHistory(
                    "EXPIRED",
                    removed.auctionId(),
                    removed.listingId(),
                    removed.sellerId(),
                    null,
                    removed.price(),
                    0,
                    removed.economyType(),
                    0
            );
            externalNotifier.expired(removed);
            return true;
        }
    }

    public List<AuctionListing> myListings(UUID sellerId, String auctionId) {
        List<AuctionListing> output = new ArrayList<>();
        for (AuctionListing listing : repository.listAll()) {
            if (!listing.sellerId().equals(sellerId)) {
                continue;
            }
            if (auctionId != null && !listing.auctionId().equalsIgnoreCase(auctionId)) {
                continue;
            }
            output.add(listing);
        }
        output.sort(Comparator.comparingLong(AuctionListing::createdAtEpochMillis).reversed());
        return output;
    }

    public CancelResult cancelListing(Player seller, long listingId, boolean canCancelAny) {
        return listingLocks.withLock(listingId, () -> cancelListingLocked(seller, listingId, canCancelAny));
    }

    private CancelResult cancelListingLocked(Player seller, long listingId, boolean canCancelAny) {
        try (RemovedListing hold = RemovedListing.take(repository, listingId)) {
            AuctionListing listing = hold.listing().orElse(null);
            if (listing == null) {
                return CancelResult.failure(CancelFailure.NOT_FOUND);
            }
            if (!canCancelAny && !listing.sellerId().equals(seller.getUniqueId())) {
                return CancelResult.failure(CancelFailure.NOT_OWNER);
            }
            ItemStack item = ItemStackCodec.decode(listing.itemBase64());
            Map<Integer, ItemStack> leftovers = seller.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                runtimeStorage.addClaim(
                        seller.getUniqueId(),
                        listing.auctionId(),
                        listing.listingId(),
                        listing.itemBase64(),
                        "CANCELLED"
                );
                hold.commit();
                repository.flush();
                invalidateListingCache(listing.auctionId());
                runtimeStorage.addHistory(
                        "CANCELLED_TO_CLAIM",
                        listing.auctionId(),
                        listing.listingId(),
                        listing.sellerId(),
                        null,
                        listing.price(),
                        0,
                        listing.economyType(),
                        0
                );
                return CancelResult.success(true);
            }
            hold.commit();
            repository.flush();
            invalidateListingCache(listing.auctionId());
            runtimeStorage.addHistory(
                    "CANCELLED",
                    listing.auctionId(),
                    listing.listingId(),
                    listing.sellerId(),
                    null,
                    listing.price(),
                    0,
                    listing.economyType(),
                    0
            );
            return CancelResult.success(false);
        }
    }

    public ClaimResult claim(Player player, boolean claimAll) {
        List<ClaimEntry> claims = runtimeStorage.listClaims(player.getUniqueId());
        if (claims.isEmpty()) {
            return new ClaimResult(0, 0);
        }
        int claimed = 0;
        int failed = 0;
        for (ClaimEntry claim : claims) {
            if (!claimAll && claimed > 0) {
                break;
            }
            ClaimEntry removed = runtimeStorage.removeClaim(claim.claimId(), player.getUniqueId());
            if (removed == null) {
                continue;
            }
            ItemStack item = ItemStackCodec.decode(removed.itemBase64());
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            if (!leftovers.isEmpty()) {
                runtimeStorage.restoreClaim(removed);
                failed++;
                continue;
            }
            claimed++;
        }
        return new ClaimResult(claimed, failed);
    }

    public int pendingClaims(UUID ownerId) {
        return runtimeStorage.listClaims(ownerId).size();
    }

    public List<PendingSaleNotification> takePendingSaleNotifications(UUID playerId) {
        return runtimeStorage.takePendingSaleNotifications(playerId);
    }

    public void setLimitOverride(UUID playerId, String scope, int value) {
        runtimeStorage.setLimitOverride(playerId, scope, value);
    }

    public int getLimitOverride(UUID playerId, String scope) {
        return runtimeStorage.getLimitOverride(playerId, scope);
    }

    public int resolveEffectiveAuctionLimit(Player player, String auctionId) {
        return limitResolver.auctionLimit(player, auctionId.toLowerCase(Locale.ROOT));
    }

    public int resolveEffectiveGlobalLimit(Player player) {
        return limitResolver.globalLimit(player);
    }

    public int activeListingsCount(UUID ownerId, String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return myListings(ownerId, null).size();
        }
        return myListings(ownerId, auctionId).size();
    }

    public int totalListingsCount() {
        return repository.listAll().size();
    }

    public List<DealHistoryEntry> recentSales(String auctionId, int limit) {
        List<DealHistoryEntry> sales = new ArrayList<>();
        for (DealHistoryEntry entry : runtimeStorage.recentHistory(Math.max(50, limit * 3))) {
            if (!"SOLD".equalsIgnoreCase(entry.action())) {
                continue;
            }
            if (auctionId != null && !auctionId.isBlank() && !entry.auctionId().equalsIgnoreCase(auctionId)) {
                continue;
            }
            sales.add(entry);
            if (sales.size() >= limit) {
                break;
            }
        }
        return sales;
    }

    public AuctionListing listingById(long listingId) {
        return repository.findById(listingId);
    }

    public List<DealHistoryEntry> playerSalesHistory(UUID playerId, String auctionId, int limit) {
        return runtimeStorage.playerHistory(playerId, "SOLD", false, true, auctionId, limit);
    }

    public List<DealHistoryEntry> playerPurchases(UUID playerId, String auctionId, int limit) {
        return runtimeStorage.playerHistory(playerId, "SOLD", true, false, auctionId, limit);
    }

    public List<ClaimEntry> expiredClaims(UUID playerId) {
        return runtimeStorage.claimsByReasons(playerId, List.of("EXPIRED", "CANCELLED", "CANCELLED_TO_CLAIM"));
    }

    public EditPriceResult editListingPrice(Player seller, long listingId, int newPrice) {
        return listingLocks.withLock(listingId, () -> editListingPriceLocked(seller, listingId, newPrice));
    }

    private EditPriceResult editListingPriceLocked(Player seller, long listingId, int newPrice) {
        AuctionListing listing = repository.findById(listingId);
        if (listing == null) {
            return EditPriceResult.LISTING_UNAVAILABLE;
        }
        if (!listing.sellerId().equals(seller.getUniqueId())) {
            return EditPriceResult.NOT_OWNER;
        }
        AuctionDefinitionSettings definition = findAuction(listing.auctionId(), configSupplier.get());
        if (definition == null) {
            return EditPriceResult.LISTING_UNAVAILABLE;
        }
        PriceLimitResolver.PriceBounds bounds = priceLimitResolver.resolve(seller, definition, settings().limits);
        if (!bounds.isValid(newPrice)) {
            return EditPriceResult.INVALID_PRICE;
        }
        if (!repository.updatePrice(listingId, newPrice)) {
            return EditPriceResult.LISTING_UNAVAILABLE;
        }
        repository.flush();
        invalidateListingCache(listing.auctionId());
        runtimeStorage.addHistory(
                "PRICE_EDIT",
                listing.auctionId(),
                listing.listingId(),
                listing.sellerId(),
                null,
                newPrice,
                0,
                listing.economyType(),
                0
        );
        return EditPriceResult.SUCCESS;
    }

    public List<AuctionListing> page(String auctionId, AuctionSort sort, AuctionCategory category, int page, int pageSize) {
        return page(auctionId, sort, category, page, pageSize, null);
    }

    public BrowsePage browsePage(
            String auctionId,
            AuctionSort sort,
            AuctionCategory category,
            int page,
            int pageSize,
            String searchQuery,
            UUID viewerId,
            BrowseFilterState filter
    ) {
        return browseService.browsePage(auctionId, sort, category, page, pageSize, searchQuery, viewerId, filter);
    }

    public BrowsePage browsePage(String auctionId, AuctionSort sort, AuctionCategory category, int page, int pageSize, String searchQuery) {
        return browsePage(auctionId, sort, category, page, pageSize, searchQuery, null, BrowseFilterState.empty());
    }

    public List<AuctionListing> page(String auctionId, AuctionSort sort, AuctionCategory category, int page, int pageSize, String searchQuery) {
        return browsePage(auctionId, sort, category, page, pageSize, searchQuery).listings();
    }

    public int count(String auctionId, AuctionCategory category, String searchQuery, UUID viewerId, BrowseFilterState filter) {
        return browseService.count(auctionId, category, searchQuery, viewerId, filter);
    }

    public String formatPrice(int price, AuctionEconomyType type) {
        return economy.format(price, type);
    }

    public List<String> listingLoreTemplate(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null || definition.listingLoreTemplate == null) {
            return List.of();
        }
        return definition.listingLoreTemplate;
    }

    public String defaultAuctionId() {
        return settings().defaultAuctionId.toLowerCase(Locale.ROOT);
    }

    public boolean auctionExists(String auctionId) {
        return findAuction(auctionId, configSupplier.get()) != null;
    }

    public boolean canOpenAuction(Player player, String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        return definition != null && hasPermission(player, definition.openPermission);
    }

    public String auctionDisplayName(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        return definition == null ? auctionId : definition.displayName;
    }

    public int expireCheckIntervalSeconds() {
        return Math.max(5, settings().limits.expireCheckIntervalSeconds);
    }

    public boolean hasVault() {
        return economy.hasVault();
    }

    public boolean hasPlayerPoints() {
        return economy.hasPlayerPoints();
    }

    public boolean hasExperienceEconomy() {
        return true;
    }

    public boolean hasCoinsEngine() {
        return economy.isAvailable(AuctionEconomyType.COINS_ENGINE);
    }

    public AuctionEconomyType economyType(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        return definition == null ? AuctionEconomyType.VAULT : AuctionEconomyType.fromString(definition.economy);
    }

    public int maxPrice(Player player, String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null) {
            return settings().limits.maxPrice;
        }
        return priceLimitResolver.resolve(player, definition, settings().limits).maxPrice();
    }

    public PriceLimitResolver.PriceBounds priceBounds(Player player, String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null) {
            return new PriceLimitResolver.PriceBounds(settings().limits.minPrice, settings().limits.maxPrice);
        }
        return priceLimitResolver.resolve(player, definition, settings().limits);
    }

    @Deprecated
    public int maxPrice() {
        return settings().limits.maxPrice;
    }

    public List<DealHistoryEntry> adminHistoryForPlayer(UUID playerId, int limit) {
        List<DealHistoryEntry> output = new ArrayList<>(runtimeStorage.playerHistory(playerId, null, true, true, null, limit));
        output.sort(Comparator.comparingLong(DealHistoryEntry::createdAtEpochMillis).reversed());
        return output.size() > limit ? output.subList(0, limit) : output;
    }

    public int purgeHistoryOlderThanDays(int days) {
        return days <= 0 ? 0 : runtimeStorage.purgeHistoryOlderThan(days * 86_400_000L);
    }

    public ClaimEntry adminRecoverClaim(long claimId) {
        return runtimeStorage.removeClaimById(claimId);
    }

    public void adminBlacklistAdd(UUID playerId, UUID actorId, String actorName) {
        runtimeStorage.addRuntimeBlacklist(playerId);
        runtimeStorage.addAudit(actorId, actorName, "BLACKLIST_ADD", playerId.toString());
    }

    public void adminBlacklistRemove(UUID playerId, UUID actorId, String actorName) {
        runtimeStorage.removeRuntimeBlacklist(playerId);
        runtimeStorage.addAudit(actorId, actorName, "BLACKLIST_REMOVE", playerId.toString());
    }

    public List<AuditLogEntry> recentAudit(int limit) {
        return runtimeStorage.recentAudit(limit);
    }

    public void audit(UUID actorId, String actorName, String action, String details) {
        runtimeStorage.addAudit(actorId, actorName, action, details);
    }

    public void restoreClaimEntry(ClaimEntry entry) {
        runtimeStorage.restoreClaim(entry);
    }

    private AuctionSettings settings() {
        return configSupplier.get().auctionSettings();
    }

    private AuctionDefinitionSettings findAuction(String auctionId, PluginConfig config) {
        String normalized = auctionId.toLowerCase(Locale.ROOT);
        for (AuctionDefinitionSettings definition : config.auctionDefinitions()) {
            if (definition.id.equalsIgnoreCase(normalized)) {
                return definition;
            }
        }
        return null;
    }

    private boolean hasPermission(Player player, String permission) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private AuctionListingCreator.DefinitionLookup definitionLookup() {
        return new AuctionListingCreator.DefinitionLookup() {
            @Override
            public AuctionDefinitionSettings find(String auctionId) {
                return findAuction(auctionId, configSupplier.get());
            }

            @Override
            public boolean hasPermission(Player player, String permission) {
                return AuctionService.this.hasPermission(player, permission);
            }
        };
    }

    private AuctionPurchaseService.ListingDefinitionLookup purchaseDefinitionLookup() {
        return new AuctionPurchaseService.ListingDefinitionLookup() {
            @Override
            public AuctionDefinitionSettings find(String auctionId) {
                return findAuction(auctionId, configSupplier.get());
            }

            @Override
            public boolean hasPermission(Player player, String permission) {
                return AuctionService.this.hasPermission(player, permission);
            }
        };
    }

    private final class LimitResolverImpl implements AuctionListingCreator.LimitResolver {
        @Override
        public int auctionLimit(Player player, String auctionId) {
            int byPermission = permissionLimitResolver.resolveAuctionLimit(
                    player,
                    auctionId,
                    settings().limits.defaultMaxActiveListingsPerAuction
            );
            int byCommand = runtimeStorage.getLimitOverride(player.getUniqueId(), auctionId);
            return Math.max(byPermission, byCommand);
        }

        @Override
        public int globalLimit(Player player) {
            int byPermission = permissionLimitResolver.resolveGlobalLimit(
                    player,
                    settings().limits.defaultMaxActiveListingsGlobal
            );
            int byCommand = runtimeStorage.getLimitOverride(player.getUniqueId(), "all");
            return Math.max(byPermission, byCommand);
        }
    }
}
