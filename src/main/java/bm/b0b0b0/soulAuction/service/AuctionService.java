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
import bm.b0b0b0.soulAuction.model.AuctionStatType;
import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.PendingExpiredListingNotification;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import bm.b0b0b0.soulAuction.model.StorageMode;
import bm.b0b0b0.soulAuction.model.result.CancelFailure;
import bm.b0b0b0.soulAuction.model.result.CancelResult;
import bm.b0b0b0.soulAuction.model.result.ClaimResult;
import bm.b0b0b0.soulAuction.model.result.EditPriceResult;
import bm.b0b0b0.soulAuction.model.result.PurchaseFailure;
import bm.b0b0b0.soulAuction.model.result.PurchaseQuote;
import bm.b0b0b0.soulAuction.model.result.PurchaseResult;
import bm.b0b0b0.soulAuction.model.result.SellFailure;
import bm.b0b0b0.soulAuction.model.result.SellResult;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowseFilterState;
import bm.b0b0b0.soulAuction.service.browse.AuctionBrowseService.BrowsePage;
import bm.b0b0b0.soulAuction.service.customitem.CustomItemRuleEngine;
import bm.b0b0b0.soulAuction.service.economy.AuctionEconomyService;
import bm.b0b0b0.soulAuction.service.fakeactivity.FakeActivityService;
import bm.b0b0b0.soulAuction.service.economy.ItemCurrencyService;
import com.google.gson.Gson;
import bm.b0b0b0.soulAuction.service.migration.AuctionStorageMigrator;
import bm.b0b0b0.soulAuction.service.listing.AuctionListingCreator;
import bm.b0b0b0.soulAuction.service.listing.AuctionPurchaseService;
import bm.b0b0b0.soulAuction.service.listing.ListingLockRunner;
import bm.b0b0b0.soulAuction.service.listing.ListingSaleClaimer;
import bm.b0b0b0.soulAuction.service.policy.AuctionSellPolicy;
import bm.b0b0b0.soulAuction.service.policy.AuctionTradeRegionPolicy;
import bm.b0b0b0.soulAuction.service.region.RegionListingHelper;
import bm.b0b0b0.soulAuction.util.ListingSearchResolveCache;
import bm.b0b0b0.soulAuction.util.ItemDisplayNames;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.PlayerDisplayNames;
import bm.b0b0b0.soulAuction.util.PlayerSkullTextures;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import bm.b0b0b0.soulAuction.util.SyntheticSellerIds;
import bm.b0b0b0.soulAuction.util.SimilarItemInventory;
import bm.b0b0b0.soulAuction.util.ListingDurationFormat;
import java.util.HashMap;
import java.util.HashSet;
import net.kyori.adventure.text.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

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
    private final AuctionTradeRegionPolicy tradeRegionPolicy;
    private final AuctionBrowseService browseService;
    private final ListingLockRunner listingLocks;
    private final ListingSaleClaimer listingSaleClaimer;
    private final AuctionExternalNotifier externalNotifier;
    private final MessageService messageService;
    private final AuctionAnnouncementBroadcaster announcementBroadcaster;
    private volatile SellerSkinBridge sellerSkinBridge;
    private volatile FakeActivityService fakeActivityService;
    private volatile SyntheticListingCounts syntheticListingCounts;
    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, BrowsePreferences> browsePreferences;
    private final ConcurrentHashMap<UUID, BrowseFilterState> browseFilters;
    private final ConcurrentHashMap<UUID, BrowseSelection> browseSelections;
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
            AuctionListingCache listingCache,
            JavaPlugin plugin
    ) {
        this.repository = repository;
        this.configSupplier = configSupplier;
        this.plugin = plugin;
        this.economy = new AuctionEconomyService(
                vaultBridge,
                playerPointsBridge,
                experienceBridge,
                coinsEngineBridge,
                new ItemCurrencyService()
        );
        this.permissionLimitResolver = permissionLimitResolver;
        this.redisSellGuard = redisSellGuard;
        this.runtimeStorage = runtimeStorage;
        this.taxPolicyResolver = taxPolicyResolver;
        this.priceLimitResolver = priceLimitResolver;
        this.listingCache = listingCache;
        this.externalNotifier = externalNotifier;
        this.messageService = messageService;
        this.announcementBroadcaster = new AuctionAnnouncementBroadcaster(configSupplier, messageService, economy);
        this.listingLocks = new ListingLockRunner();
        AuctionSellPolicy sellPolicy = new AuctionSellPolicy(runtimeStorage);
        AuctionTradeRegionPolicy tradeRegionPolicy = new AuctionTradeRegionPolicy();
        CustomItemRuleEngine customItemRuleEngine = new CustomItemRuleEngine();
        java.util.function.Consumer<String> invalidate = this::invalidateListingCache;
        java.util.function.BiConsumer<String, AuctionListing> listingSync = this::publishListingNetworkChange;
        ListingSaleClaimer listingSaleClaimer = new ListingSaleClaimer(repository, redisSellGuard);
        this.listingSaleClaimer = listingSaleClaimer;
        this.tradeRegionPolicy = tradeRegionPolicy;
        this.listingCreator = new AuctionListingCreator(
                repository,
                configSupplier,
                economy,
                permissionLimitResolver,
                priceLimitResolver,
                redisSellGuard,
                runtimeStorage,
                sellPolicy,
                tradeRegionPolicy,
                externalNotifier,
                customItemRuleEngine,
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
                listingLocks,
                listingSaleClaimer,
                tradeRegionPolicy,
                invalidate,
                listingSync,
                announcementBroadcaster
        );
        this.browseService = new AuctionBrowseService(
                repository,
                listingCache,
                runtimeStorage,
                priorityResolver,
                () -> configSupplier.get().auctionSettings()
        );
        this.browsePreferences = new ConcurrentHashMap<>();
        this.browseFilters = new ConcurrentHashMap<>();
        this.browseSelections = new ConcurrentHashMap<>();
        this.pendingChatSearch = new ConcurrentHashMap<>();
        this.playerSellLocks = new ConcurrentHashMap<>();
    }

    public record BrowsePreferences(String auctionId, int page, String searchQuery) {
    }

    public record PendingChatSearch(String auctionId) {
    }

    public record BrowseSelection(AuctionSort sort, AuctionCategory category, String auctionId) {
    }

    public void recordBrowseSelection(UUID viewerId, AuctionSort sort, AuctionCategory category, String auctionId) {
        browseSelections.put(viewerId, new BrowseSelection(sort, category, auctionId));
    }

    public BrowseSelection browseSelection(UUID viewerId) {
        BrowseSelection selection = browseSelections.get(viewerId);
        if (selection != null) {
            return selection;
        }
        return new BrowseSelection(AuctionSort.NEWEST, AuctionCategory.ALL, defaultAuctionId());
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

    public void clearBrowseSearch(UUID playerId) {
        BrowseFilterState current = browseFilterState(playerId);
        setBrowseFilterState(playerId, current.withSearch(null));
        BrowsePreferences preferences = browsePreferences.get(playerId);
        if (preferences != null) {
            setBrowsePreferences(playerId, new BrowsePreferences(
                    preferences.auctionId(),
                    preferences.page(),
                    null
            ));
        }
    }

    public void beginPendingChatSearch(UUID playerId, String auctionId) {
        pendingChatSearch.put(playerId, new PendingChatSearch(auctionId.toLowerCase(Locale.ROOT)));
    }

    public void cancelPendingChatSearch(UUID playerId) {
        pendingChatSearch.remove(playerId);
    }

    public Optional<PendingChatSearch> peekPendingChatSearch(UUID playerId) {
        return Optional.ofNullable(pendingChatSearch.get(playerId));
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

    public List<UUID> favoriteSellers(UUID viewerId) {
        return runtimeStorage.favoriteSellers(viewerId);
    }

    public boolean toggleFavoriteListing(UUID viewerId, long listingId) {
        return runtimeStorage.toggleFavoriteListing(viewerId, listingId);
    }

    public boolean isFavoriteListing(UUID viewerId, long listingId) {
        return runtimeStorage.isFavoriteListing(viewerId, listingId);
    }

    public List<AuctionListing> favoriteListingsForViewer(UUID viewerId) {
        java.util.Set<Long> ids = runtimeStorage.favoriteListings(viewerId);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<AuctionListing> listings = new ArrayList<>();
        for (long listingId : ids) {
            AuctionListing listing = repository.findById(listingId);
            if (listing == null) {
                if (runtimeStorage.isFavoriteListing(viewerId, listingId)) {
                    runtimeStorage.toggleFavoriteListing(viewerId, listingId);
                }
                continue;
            }
            listings.add(listing);
        }
        listings.sort(Comparator.comparingLong(AuctionListing::createdAtEpochMillis).reversed());
        return listings;
    }

    public void invalidateListingCache(String auctionId) {
        listingCache.invalidate(auctionId);
        syntheticListingCounts = null;
        ListingSearchResolveCache.clear();
        redisSellGuard.publishCacheInvalidate(auctionId);
    }

    public void invalidateListingCache(String auctionId, AuctionListing listing, boolean removed) {
        listingCache.invalidate(auctionId);
        syntheticListingCounts = null;
        if (listing != null) {
            ListingSearchResolveCache.invalidate(listing.listingId());
            publishListingNetworkChange(removed ? "REMOVE" : "UPSERT", listing);
        } else {
            ListingSearchResolveCache.clear();
        }
        redisSellGuard.publishCacheInvalidate(auctionId);
    }

    public AuctionAnnouncementBroadcaster announcementBroadcaster() {
        return announcementBroadcaster;
    }

    public void publishListingChange(String action, AuctionListing listing) {
        publishListingNetworkChange(action, listing);
    }

    public ListingLockRunner listingLocks() {
        return listingLocks;
    }

    public ListingSaleClaimer listingSaleClaimer() {
        return listingSaleClaimer;
    }

    public AuctionEconomyService economyService() {
        return economy;
    }

    private void publishListingNetworkChange(String action, AuctionListing listing) {
        AuctionSettings settings = settings();
        if (settings.storage == null || settings.storage.redis == null || !settings.storage.redis.redisFullListingSync) {
            return;
        }
        if (listing == null) {
            return;
        }
        redisSellGuard.publishListingChange(action, listing, new Gson());
    }

    public void attachCacheSubscriber() {
        Gson gson = new Gson();
        redisSellGuard.startCacheSubscriber(message -> {
            if (message == null || message.isBlank()) {
                return;
            }
            if (message.startsWith("L+")) {
                AuctionListing listing = gson.fromJson(message.substring(2), AuctionListing.class);
                if (listing != null) {
                    listingCache.applyNetworkListing(listing);
                }
                return;
            }
            if (message.startsWith("L-")) {
                int separator = message.indexOf('|', 2);
                if (separator > 2) {
                    long listingId = Long.parseLong(message.substring(2, separator));
                    String auctionId = message.substring(separator + 1);
                    listingCache.removeNetworkListing(listingId, auctionId);
                }
                return;
            }
            if (payloadEqualsStar(message)) {
                listingCache.invalidateAll();
                listingCache.clearNetworkOverlay();
                return;
            }
            listingCache.invalidate(message);
        });
    }

    private static boolean payloadEqualsStar(String message) {
        return "*".equals(message);
    }

    public String listingCacheStats() {
        return "buckets=" + listingCache.cachedAuctionBuckets()
                + ", listings=" + listingCache.cachedListingCount()
                + ", networkOverlay=" + listingCache.networkOverlaySize();
    }

    public void rebuildListingCache() {
        listingCache.invalidateAll();
        listingCache.clearNetworkOverlay();
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

    private volatile boolean loaded;

    public boolean isLoaded() {
        return loaded;
    }

    public CompletableFuture<Void> load() {
        loaded = false;
        return repository.load()
                .thenCompose(unused -> runtimeStorage.load())
                .whenComplete((unused, throwable) -> {
                    if (throwable == null) {
                        loaded = true;
                    }
                });
    }

    public CompletableFuture<Void> flush() {
        return repository.flush().thenCompose(unused -> runtimeStorage.flush());
    }

    public CompletableFuture<Void> close() {
        return flush().thenCompose(unused -> repository.close()).thenCompose(unused -> runtimeStorage.close());
    }

    public SellResult createListing(Player seller, String auctionId, int price) {
        return createListing(seller, auctionId, price, 0);
    }

    public SellResult createListing(Player seller, String auctionId, int price, int amount) {
        if (!loaded) {
            return SellResult.failure(SellFailure.STORAGE_NOT_READY);
        }
        ItemStack itemInHand = seller.getInventory().getItemInMainHand();
        if (itemInHand.isEmpty()) {
            return SellResult.failure(SellFailure.EMPTY_HAND);
        }
        int sellAmount = amount <= 0 ? itemInHand.getAmount() : amount;
        if (sellAmount > itemInHand.getAmount()) {
            return SellResult.failure(SellFailure.INVALID_AMOUNT);
        }
        ItemStack soldItem = itemInHand.clone();
        soldItem.setAmount(sellAmount);
        Object sellLock = playerSellLocks.computeIfAbsent(seller.getUniqueId(), ignored -> new Object());
        synchronized (sellLock) {
            SellResult result = listingCreator.create(seller, auctionId, price, soldItem, definitionLookup());
            if (result.success()) {
                if (sellAmount >= itemInHand.getAmount()) {
                    seller.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                } else {
                    itemInHand.setAmount(itemInHand.getAmount() - sellAmount);
                }
                if (result.listing() != null) {
                    invalidateListingCache(result.listing().auctionId(), result.listing(), false);
                }
            }
            return result;
        }
    }

    public SellResult createListingFromItem(Player seller, String auctionId, int price, ItemStack item) {
        return createListingFromItem(seller, auctionId, price, item, item == null ? 0 : item.getAmount());
    }

    public SellResult createListingFromItem(Player seller, String auctionId, int price, ItemStack item, int amount) {
        if (!loaded) {
            return SellResult.failure(SellFailure.STORAGE_NOT_READY);
        }
        if (item == null || item.isEmpty()) {
            return SellResult.failure(SellFailure.EMPTY_HAND);
        }
        if (item.getAmount() < 1) {
            return SellResult.failure(SellFailure.INVALID_AMOUNT);
        }
        int sellAmount = amount <= 0 ? item.getAmount() : amount;
        if (sellAmount < 1 || sellAmount > item.getAmount()) {
            return SellResult.failure(SellFailure.INVALID_AMOUNT);
        }
        ItemStack template = item.clone();
        template.setAmount(1);
        int removed = SimilarItemInventory.removeSimilar(
                seller.getInventory(),
                template,
                sellAmount
        );
        if (removed < sellAmount) {
            return SellResult.failure(SellFailure.INVALID_AMOUNT);
        }
        ItemStack soldItem = item.clone();
        soldItem.setAmount(sellAmount);
        return createListingFromEscrow(seller, auctionId, price, soldItem);
    }

    public SellResult createListingFromEscrow(Player seller, String auctionId, int price, ItemStack escrowItem) {
        return createListingFromEscrow(seller, auctionId, price, escrowItem, escrowItem == null ? 0 : escrowItem.getAmount());
    }

    public SellResult createListingFromEscrow(Player seller, String auctionId, int price, ItemStack escrowItem, int sellAmount) {
        if (!loaded) {
            return SellResult.failure(SellFailure.STORAGE_NOT_READY);
        }
        if (escrowItem == null || escrowItem.isEmpty()) {
            return SellResult.failure(SellFailure.EMPTY_HAND);
        }
        if (sellAmount < 1 || sellAmount > escrowItem.getAmount()) {
            return SellResult.failure(SellFailure.INVALID_AMOUNT);
        }
        ItemStack soldItem = escrowItem.clone();
        soldItem.setAmount(sellAmount);
        if (soldItem.getAmount() != sellAmount) {
            return SellResult.failure(SellFailure.INVALID_AMOUNT);
        }
        Object sellLock = playerSellLocks.computeIfAbsent(seller.getUniqueId(), ignored -> new Object());
        synchronized (sellLock) {
            SellResult result = listingCreator.create(seller, auctionId, price, soldItem, definitionLookup());
            if (result.success() && result.listing() != null) {
                invalidateListingCache(result.listing().auctionId(), result.listing(), false);
            }
            return result;
        }
    }

    public SellResult createAdminFakeListing(String sellerName, String auctionId, int price, ItemStack escrowItem) {
        return createAdminFakeListing(sellerName, auctionId, price, escrowItem, System.currentTimeMillis());
    }

    public SellResult createAdminFakeListing(
            String sellerName,
            String auctionId,
            int price,
            ItemStack escrowItem,
            long createdAtEpochMillis
    ) {
        if (!loaded) {
            return SellResult.failure(SellFailure.STORAGE_NOT_READY);
        }
        if (escrowItem == null || escrowItem.isEmpty()) {
            return SellResult.failure(SellFailure.EMPTY_HAND);
        }
        ItemStack soldItem = escrowItem.clone();
        SellResult result = listingCreator.createSynthetic(
                sellerName,
                auctionId,
                price,
                soldItem,
                definitionLookup(),
                createdAtEpochMillis
        );
        if (result.success() && result.listing() != null) {
            runtimeStorage.rememberSyntheticSeller(result.listing().sellerId(), result.listing().sellerName());
            FakeActivityService activity = fakeActivityService;
            if (activity != null) {
                activity.registerFromAdminFake(result.listing().sellerName(), soldItem, auctionId);
            }
            invalidateListingCache(result.listing().auctionId(), result.listing(), false);
            prefetchSellerSkin(result.listing().sellerName(), result.listing().sellerId());
        }
        return result;
    }

    public List<String> knownFakeSellerNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        FakeActivityService activity = fakeActivityService;
        if (activity != null) {
            for (String name : activity.sellerNames()) {
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        for (String name : runtimeStorage.knownSyntheticSellerNames()) {
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    public void attachFakeActivityService(FakeActivityService service) {
        this.fakeActivityService = service;
    }

    public int countSyntheticListings(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return syntheticListingCounts().total();
        }
        int count = 0;
        for (AuctionListing listing : listingsForAuction(auctionId)) {
            if (listing.metadata().syntheticSeller) {
                count++;
            }
        }
        return count;
    }

    public boolean syntheticSellerHasActiveListing(String sellerName, String auctionId) {
        return countSyntheticListingsForSeller(sellerName, auctionId) > 0;
    }

    public int countSyntheticListingsForSeller(String sellerName, String auctionId) {
        if (sellerName == null || sellerName.isBlank() || auctionId == null || auctionId.isBlank()) {
            return 0;
        }
        UUID sellerId = SyntheticSellerIds.forDisplayName(sellerName.trim());
        int count = 0;
        for (AuctionListing listing : listingsForAuction(auctionId)) {
            if (!listing.sellerId().equals(sellerId)) {
                continue;
            }
            if (!listing.metadata().syntheticSeller) {
                continue;
            }
            count++;
        }
        return count;
    }

    public boolean syntheticSellerCanListMore(String sellerName, String auctionId, int maxPerSeller) {
        if (maxPerSeller <= 0) {
            return false;
        }
        return countSyntheticListingsForSeller(sellerName, auctionId) < maxPerSeller;
    }

    public String resolveSellerDisplayName(UUID sellerId) {
        String synthetic = runtimeStorage.syntheticSellerName(sellerId);
        if (synthetic != null && !synthetic.isBlank()) {
            return synthetic;
        }
        for (AuctionListing listing : repository.listAll()) {
            if (listing.sellerId().equals(sellerId)) {
                return listing.sellerName();
            }
        }
        return PlayerDisplayNames.resolve(sellerId, null);
    }

    private SellerSkinBridge sellerSkinBridge() {
        SellerSkinBridge bridge = sellerSkinBridge;
        if (bridge != null) {
            return bridge;
        }
        synchronized (this) {
            bridge = sellerSkinBridge;
            if (bridge == null) {
                sellerSkinBridge = bridge = new SellerSkinBridge(plugin, configSupplier);
            }
            return bridge;
        }
    }

    public boolean isSyntheticSeller(UUID sellerId) {
        if (sellerId == null) {
            return false;
        }
        String synthetic = runtimeStorage.syntheticSellerName(sellerId);
        if (synthetic != null && !synthetic.isBlank()) {
            return true;
        }
        for (AuctionListing listing : repository.listAll()) {
            if (listing.sellerId().equals(sellerId) && listing.metadata().syntheticSeller) {
                return true;
            }
        }
        return false;
    }

    public List<String> sellerSkinWarmupTargets() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        var settings = configSupplier.get().auctionSettings().sellerSkins;
        String forced = settings.fakeSellerSkin == null ? "" : settings.fakeSellerSkin.trim();
        if (!forced.isEmpty()) {
            names.add(forced);
        }
        if (settings.fallbackSkin != null && !settings.fallbackSkin.isBlank()) {
            names.add(settings.fallbackSkin.trim());
        }
        return List.copyOf(names);
    }

    public void applySellerSkinsToMenu(Player viewer, Inventory inventory, Map<Integer, UUID> sellersBySlot) {
        SellerSkinBridge bridge = sellerSkinBridge();
        if (!bridge.enabled() || viewer == null || inventory == null || sellersBySlot.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, UUID> entry : sellersBySlot.entrySet()) {
            int slot = entry.getKey();
            UUID sellerId = entry.getValue();
            String sellerName = resolveSellerDisplayName(sellerId);
            boolean synthetic = isSyntheticSeller(sellerId);
            bridge.fetchSkinProperty(sellerName, sellerId, synthetic).thenAccept(property -> {
                if (property.isEmpty()) {
                    return;
                }
                PluginSchedulers.run(plugin, viewer, () -> applySellerSkinToSlot(
                        viewer,
                        inventory,
                        slot,
                        sellerId,
                        sellerName,
                        property.get()
                ));
            });
        }
    }

    public CompletableFuture<SkinRestorerBridge.WarmupResult> warmupSellerSkins() {
        return sellerSkinBridge().warmupSkinsRestorerCache(sellerSkinWarmupTargets());
    }

    public void prefetchSellerSkin(String sellerName, UUID sellerId) {
        SellerSkinBridge bridge = sellerSkinBridge();
        String prefetchName = resolveSkinPrefetchName(sellerName, sellerId);
        if (prefetchName != null) {
            bridge.prefetchSkinsRestorer(prefetchName);
        }
        if (bridge.enabled()) {
            bridge.fetchSkinProperty(sellerName, sellerId, isSyntheticSeller(sellerId));
        }
    }

    private String resolveSkinPrefetchName(String sellerName, UUID sellerId) {
        var settings = configSupplier.get().auctionSettings().sellerSkins;
        if (isSyntheticSeller(sellerId)) {
            String forced = settings.fakeSellerSkin == null ? "" : settings.fakeSellerSkin.trim();
            if (!forced.isEmpty()) {
                return forced;
            }
            String fallback = settings.fallbackSkin == null ? "" : settings.fallbackSkin.trim();
            return fallback.isEmpty() ? null : fallback;
        }
        return sellerName;
    }

    private void applySellerSkinToSlot(
            Player viewer,
            Inventory inventory,
            int slot,
            UUID sellerId,
            String sellerName,
            SkinTexture texture
    ) {
        if (!viewer.isOnline() || viewer.getOpenInventory().getTopInventory() != inventory) {
            return;
        }
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType() != Material.PLAYER_HEAD) {
            return;
        }
        ItemStack updated = current.clone();
        ItemMeta meta = updated.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            PlayerSkullTextures.apply(skullMeta, sellerId, sellerName, texture);
            updated.setItemMeta(skullMeta);
            inventory.setItem(slot, updated);
        }
    }

    public PurchaseResult purchase(Player buyer, long listingId) {
        if (!loaded) {
            return PurchaseResult.failure(PurchaseFailure.STORAGE_NOT_READY);
        }
        PurchaseResult result = purchaseService.purchase(buyer, listingId, purchaseDefinitionLookup());
        if (result.success() && result.listing() != null && result.listing().metadata().syntheticSeller) {
            FakeActivityService activity = fakeActivityService;
            if (activity != null) {
                activity.onSyntheticPurchased(result.listing());
            }
        }
        return result;
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
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        TaxPolicyResolver.TaxAmounts taxes = taxPolicyResolver.resolve(
                buyer,
                sellerOnline,
                definition,
                settings(),
                listing.price(),
                item
        );
        return new PurchaseQuote(listing, taxes.buyerCharge(listing.price()), taxes.saleTax(), taxes.buyTax());
    }

    public boolean claimPendingSalePayments(Player player) {
        List<PendingSaleNotification> pending = repository.sharedPendingPayouts()
                ? repository.drainPendingPayouts(player.getUniqueId())
                : runtimeStorage.takePendingSaleNotifications(player.getUniqueId());
        if (pending.isEmpty()) {
            return false;
        }
        boolean anyPaid = false;
        List<PendingSaleNotification> failed = new ArrayList<>();
        for (PendingSaleNotification notification : pending) {
            AuctionDefinitionSettings definition = findAuction(notification.auctionId(), configSupplier.get());
            if (definition == null) {
                failed.add(notification);
                continue;
            }
            if (economy.deposit(player.getUniqueId(), notification.payout(), notification.economyType(), definition)) {
                anyPaid = true;
            } else {
                failed.add(notification);
            }
        }
        for (PendingSaleNotification notification : failed) {
            if (repository.sharedPendingPayouts()) {
                repository.storePendingPayout(notification);
            } else {
                runtimeStorage.addPendingSaleNotification(notification);
            }
        }
        return anyPaid;
    }

    public int expireListings() {
        int expired = 0;
        long now = System.currentTimeMillis();
        List<AuctionListing> snapshot = repository.listAll();
        for (AuctionListing listing : snapshot) {
            AuctionDefinitionSettings definition = findAuction(listing.auctionId(), configSupplier.get());
            if (definition == null || definition.listingTtlSeconds <= 0) {
                continue;
            }
            long ttlMillis = definition.listingTtlSeconds * 1000L;
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
        Optional<AuctionListing> withdrawn = withdrawListing(listingId, "EXPIRED");
        AuctionListing removed = withdrawn.orElse(null);
        if (removed == null) {
            return false;
        }
        if (RegionListingHelper.isRegionListing(removed)) {
            repository.flush();
            invalidateListingCache(removed.auctionId(), removed, true);
            runtimeStorage.addHistory(
                    "EXPIRED",
                    removed.auctionId(),
                    removed.listingId(),
                    removed.sellerId(),
                    removed.sellerName(),
                    null,
                    null,
                    removed.price(),
                    0,
                    removed.economyType(),
                    0
            );
            externalNotifier.expired(removed);
            return true;
        }
        ClaimEntry claim = runtimeStorage.addClaim(
                removed.sellerId(),
                removed.auctionId(),
                removed.listingId(),
                removed.itemBase64(),
                "EXPIRED"
        );
        notifyListingExpired(removed, claim);
        repository.flush();
        invalidateListingCache(removed.auctionId(), removed, true);
        runtimeStorage.addHistory(
                "EXPIRED",
                removed.auctionId(),
                removed.listingId(),
                removed.sellerId(),
                removed.sellerName(),
                null,
                null,
                removed.price(),
                0,
                removed.economyType(),
                0
        );
        externalNotifier.expired(removed);
        return true;
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

    public CancelResult cancelRegionListing(Player seller, long listingId, boolean canCancelAny) {
        return listingLocks.withLock(listingId, () -> cancelRegionListingLocked(seller, listingId, canCancelAny));
    }

    private CancelResult cancelRegionListingLocked(Player seller, long listingId, boolean canCancelAny) {
        Optional<AuctionListing> withdrawn = withdrawListing(listingId, "CANCELLED");
        AuctionListing listing = withdrawn.orElse(null);
        if (listing == null) {
            return CancelResult.failure(CancelFailure.NOT_FOUND);
        }
        if (!RegionListingHelper.isRegionListing(listing)) {
            rollbackWithdraw(listing, "CANCELLED");
            return CancelResult.failure(CancelFailure.NOT_FOUND);
        }
        if (!canCancelAny && !listing.sellerId().equals(seller.getUniqueId())) {
            rollbackWithdraw(listing, "CANCELLED");
            return CancelResult.failure(CancelFailure.NOT_OWNER);
        }
        repository.flush();
        invalidateListingCache(listing.auctionId(), listing, true);
        runtimeStorage.addHistory(
                "CANCELLED",
                listing.auctionId(),
                listing.listingId(),
                listing.sellerId(),
                listing.sellerName(),
                null,
                null,
                listing.price(),
                0,
                listing.economyType(),
                0
        );
        return CancelResult.success(false);
    }

    public CancelResult cancelListing(Player seller, long listingId, boolean canCancelAny) {
        return listingLocks.withLock(listingId, () -> cancelListingLocked(seller, listingId, canCancelAny));
    }

    private CancelResult cancelListingLocked(Player seller, long listingId, boolean canCancelAny) {
        Optional<AuctionListing> withdrawn = withdrawListing(listingId, "CANCELLED");
        AuctionListing listing = withdrawn.orElse(null);
        if (listing == null) {
            return CancelResult.failure(CancelFailure.NOT_FOUND);
        }
        if (!canCancelAny && !listing.sellerId().equals(seller.getUniqueId())) {
            rollbackWithdraw(listing, "CANCELLED");
            return CancelResult.failure(CancelFailure.NOT_OWNER);
        }
        if (RegionListingHelper.isRegionListing(listing)) {
            repository.flush();
            invalidateListingCache(listing.auctionId(), listing, true);
            runtimeStorage.addHistory(
                    "CANCELLED",
                    listing.auctionId(),
                    listing.listingId(),
                    listing.sellerId(),
                    listing.sellerName(),
                    null,
                    null,
                    listing.price(),
                    0,
                    listing.economyType(),
                    0
            );
            return CancelResult.success(false);
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
            repository.flush();
            invalidateListingCache(listing.auctionId(), listing, true);
            runtimeStorage.addHistory(
                    "CANCELLED_TO_CLAIM",
                    listing.auctionId(),
                    listing.listingId(),
                    listing.sellerId(),
                    listing.sellerName(),
                    null,
                    null,
                    listing.price(),
                    0,
                    listing.economyType(),
                    0
            );
            return CancelResult.success(true);
        }
        repository.flush();
        invalidateListingCache(listing.auctionId(), listing, true);
        runtimeStorage.addHistory(
                "CANCELLED",
                listing.auctionId(),
                listing.listingId(),
                listing.sellerId(),
                listing.sellerName(),
                null,
                null,
                listing.price(),
                0,
                listing.economyType(),
                0
        );
        return CancelResult.success(false);
    }

    private Optional<AuctionListing> withdrawListing(long listingId, String sqlStatus) {
        if (repository instanceof bm.b0b0b0.soulAuction.repository.SqlAuctionRepository sqlRepository) {
            return sqlRepository.claimStatusBlocking(listingId, sqlStatus);
        }
        AuctionListing removed = repository.remove(listingId);
        return removed == null ? Optional.empty() : Optional.of(removed);
    }

    private void rollbackWithdraw(AuctionListing listing, String sqlStatus) {
        if (repository instanceof bm.b0b0b0.soulAuction.repository.SqlAuctionRepository sqlRepository) {
            sqlRepository.restoreFromStatusBlocking(listing, sqlStatus);
            return;
        }
        repository.putBack(listing);
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
        if (repository.sharedPendingPayouts()) {
            return repository.drainPendingPayouts(playerId);
        }
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

    public List<AuctionDefinitionSettings> sortedAuctionDefinitions() {
        return configSupplier.get().auctionDefinitions().stream()
                .sorted(Comparator.comparing(definition -> definition.id.toLowerCase(Locale.ROOT)))
                .toList();
    }

    public AuctionDefinitionSettings findAuctionDefinition(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return null;
        }
        return findAuction(auctionId, configSupplier.get());
    }

    public int countAuctionsWithFakeActivity() {
        int count = 0;
        for (AuctionDefinitionSettings definition : sortedAuctionDefinitions()) {
            if (definition.fakeActivityEnabled) {
                count++;
            }
        }
        return count;
    }

    public int countActiveListings(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return 0;
        }
        return listingsForAuction(auctionId).size();
    }

    private List<AuctionListing> listingsForAuction(String auctionId) {
        return listingCache.listingsForAuction(
                auctionId,
                () -> repository.listByAuction(auctionId)
        );
    }

    private SyntheticListingCounts syntheticListingCounts() {
        SyntheticListingCounts snapshot = syntheticListingCounts;
        if (snapshot != null) {
            return snapshot;
        }
        Map<String, Integer> byAuction = new HashMap<>();
        int total = 0;
        for (AuctionListing listing : repository.listAll()) {
            if (!listing.metadata().syntheticSeller) {
                continue;
            }
            total++;
            String key = listing.auctionId().toLowerCase(Locale.ROOT);
            byAuction.merge(key, 1, Integer::sum);
        }
        SyntheticListingCounts computed = new SyntheticListingCounts(total, Map.copyOf(byAuction));
        syntheticListingCounts = computed;
        return computed;
    }

    private record SyntheticListingCounts(int total, Map<String, Integer> byAuction) {
    }

    public AuctionStorageMigrator.Result migrateFromStorage(
            JavaPlugin plugin,
            StorageMode sourceMode,
            boolean dryRun,
            boolean archiveSource
    ) {
        AuctionStorageMigrator migrator = new AuctionStorageMigrator();
        AuctionStorageMigrator.Result result = migrator.migrate(
                plugin,
                settings(),
                repository,
                sourceMode,
                dryRun,
                archiveSource
        );
        if (!dryRun && result.imported() > 0) {
            HashSet<String> auctionIds = new HashSet<>();
            for (AuctionListing listing : repository.listAll()) {
                if (listing.auctionId() != null) {
                    auctionIds.add(listing.auctionId().toLowerCase(Locale.ROOT));
                }
            }
            for (String auctionId : auctionIds) {
                invalidateListingCache(auctionId);
            }
        }
        return result;
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

    public int expiredClaimsCount(UUID playerId) {
        return runtimeStorage.claimsByReasons(playerId, List.of("EXPIRED")).size();
    }

    public long dealStat(UUID playerId, AuctionStatType type, String currencyKey) {
        return runtimeStorage.dealStat(playerId, type, currencyKey);
    }

    public long globalDealStat(AuctionStatType type, String currencyKey) {
        return runtimeStorage.globalDealStat(type, currencyKey);
    }

    public int sellLimit(OfflinePlayer player) {
        if (player == null) {
            return settings().limits.defaultMaxActiveListingsGlobal;
        }
        Player online = player.getPlayer();
        if (online != null) {
            return resolveEffectiveGlobalLimit(online);
        }
        int byCommand = runtimeStorage.getLimitOverride(player.getUniqueId(), "all");
        return Math.max(settings().limits.defaultMaxActiveListingsGlobal, byCommand);
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
        PriceLimitResolver.PriceBounds bounds = priceLimitResolver.resolve(seller, definition, settings(), ItemStackCodec.decode(listing.itemBase64()));
        if (!bounds.isValid(newPrice)) {
            return EditPriceResult.INVALID_PRICE;
        }
        if (!repository.updatePrice(listingId, newPrice)) {
            return EditPriceResult.LISTING_UNAVAILABLE;
        }
        repository.flush();
        AuctionListing updated = repository.findById(listingId);
        if (updated != null) {
            invalidateListingCache(updated.auctionId(), updated, false);
        } else {
            invalidateListingCache(listing.auctionId());
        }
        runtimeStorage.addHistory(
                "PRICE_EDIT",
                listing.auctionId(),
                listing.listingId(),
                listing.sellerId(),
                listing.sellerName(),
                null,
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
        return formatPrice(price, type, defaultAuctionId());
    }

    public String formatPrice(int price, AuctionEconomyType type, String auctionId) {
        return formatPrice(price, type, auctionId, (Player) null);
    }

    public String formatPrice(int price, AuctionEconomyType type, String auctionId, Player viewer) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        return economy.format(price, type, definition, viewer);
    }

    public String formatPrice(int price, String auctionId) {
        return formatPrice(price, auctionId, (UUID) null);
    }

    public String formatPrice(int price, String auctionId, UUID viewerId) {
        Player viewer = viewerId == null ? null : org.bukkit.Bukkit.getPlayer(viewerId);
        return formatPrice(price, economyType(auctionId), auctionId, viewer);
    }

    public String formatPrice(int price, String auctionId, Player viewer) {
        return formatPrice(price, economyType(auctionId), auctionId, viewer);
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
        if (definition == null) {
            return false;
        }
        if (!hasPermission(player, definition.openPermission)) {
            return false;
        }
        return tradeRegionPolicy.allowsTrade(player, definition);
    }

    public boolean guardAuctionAccess(Player player, String auctionId) {
        if (!auctionExists(auctionId)) {
            messageService.send(player, "error-auction-not-found");
            return false;
        }
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null) {
            messageService.send(player, "error-auction-not-found");
            return false;
        }
        if (!hasPermission(player, definition.openPermission)) {
            messageService.send(player, "error-open-auction-denied");
            return false;
        }
        if (!tradeRegionPolicy.allowsTrade(player, definition)) {
            messageService.send(
                    player,
                    "error-trade-region-denied",
                    tradeRegionPlaceholders(definition)
            );
            return false;
        }
        return true;
    }

    public Map<String, String> tradeRegionPlaceholders(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        return tradeRegionPlaceholders(definition);
    }

    public Map<String, String> tradeRegionPlaceholders(AuctionDefinitionSettings definition) {
        return Map.of("regions", tradeRegionPolicy.formattedAllowedRegions(definition));
    }

    public String auctionDisplayName(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null) {
            return auctionId;
        }
        if (definition.displayName == null || definition.displayName.isBlank()) {
            return auctionId;
        }
        return definition.displayName;
    }

    public Map<String, String> auctionGuiTitlePlaceholders(String auctionId) {
        String suffix = "";
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition != null && definition.displayName != null && !definition.displayName.isBlank()) {
            suffix = " " + definition.displayName.trim();
        }
        return Map.of("auction_name", suffix);
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
        return economy.isAvailable(AuctionEconomyType.COINS_ENGINE, null);
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

    public int globalMaxPrice() {
        return settings().limits.maxPrice;
    }

    public PriceLimitResolver.PriceBounds priceBounds(Player player, String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null) {
            return new PriceLimitResolver.PriceBounds(settings().limits.minPrice, settings().limits.maxPrice);
        }
        return priceLimitResolver.resolve(player, definition, settings().limits);
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

    public boolean listingExpiryEnabled(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        return definition != null && definition.listingTtlSeconds > 0;
    }

    public int listingTtlSeconds(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        return definition == null ? 0 : definition.listingTtlSeconds;
    }

    public long listingExpiresAtEpochMillis(AuctionListing listing) {
        int ttlSeconds = listingTtlSeconds(listing.auctionId());
        if (ttlSeconds <= 0) {
            return Long.MAX_VALUE;
        }
        return listing.createdAtEpochMillis() + ttlSeconds * 1000L;
    }

    public long listingRemainingMillis(AuctionListing listing) {
        long expiresAt = listingExpiresAtEpochMillis(listing);
        if (expiresAt == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    public Map<String, String> listingLorePlaceholders(AuctionListing listing, UUID viewerId) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("seller", listing.sellerName());
        placeholders.put("price", formatPrice(listing.price(), listing.auctionId(), viewerId));
        placeholders.put("id", String.valueOf(listing.listingId()));
        placeholders.put("auction", auctionDisplayName(listing.auctionId()));
        java.util.Locale locale = messageService.javaLocale(viewerId);
        if (listingExpiryEnabled(listing.auctionId())) {
            placeholders.put("expires_in", ListingDurationFormat.formatRemainingMillis(listingRemainingMillis(listing), locale));
            placeholders.put("expires_at", ListingDurationFormat.formatExpiresAtEpochMillis(listingExpiresAtEpochMillis(listing), locale));
        } else {
            placeholders.put("expires_in", "");
            placeholders.put("expires_at", "");
        }
        return placeholders;
    }

    public Map<String, String> listingCreatedMessagePlaceholders(Player player, AuctionListing listing) {
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        Map<String, String> placeholders = listingLorePlaceholders(listing, player.getUniqueId());
        java.util.Locale locale = messageService.javaLocale(player.getUniqueId());
        placeholders.put("amount", String.valueOf(Math.max(1, item.getAmount())));
        int ttlSeconds = listingTtlSeconds(listing.auctionId());
        placeholders.put("duration", ListingDurationFormat.formatSeconds(ttlSeconds, locale));
        if (ttlSeconds > 0) {
            placeholders.put("expires_at", ListingDurationFormat.formatExpiresAtEpochMillis(listingExpiresAtEpochMillis(listing), locale));
        } else {
            placeholders.put("expires_at", "");
        }
        return placeholders;
    }

    public void sendListingCreatedMessage(Player player, AuctionListing listing) {
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        Map<String, String> placeholders = listingCreatedMessagePlaceholders(player, listing);
        Map<String, Component> itemLine = Map.of("item", ItemDisplayNames.chatItem(item));
        messageService.send(player, "success-listed", placeholders, itemLine);
        if (listingExpiryEnabled(listing.auctionId())) {
            messageService.send(player, "success-listed-expiry-timed", placeholders);
        } else {
            messageService.send(player, "success-listed-expiry-unlimited");
        }
        AuctionDefinitionSettings definition = findAuction(listing.auctionId(), configSupplier.get());
        announcementBroadcaster.maybeBroadcastItemListing(player, listing, definition);
    }

    public List<PendingExpiredListingNotification> takePendingExpiredListingNotifications(UUID playerId) {
        return runtimeStorage.takePendingExpiredListingNotifications(playerId);
    }

    public void deliverExpiredListingNotification(Player player, PendingExpiredListingNotification notification) {
        ItemStack stack = ItemStack.empty();
        for (ClaimEntry claim : runtimeStorage.listClaims(player.getUniqueId())) {
            if (claim.claimId() == notification.claimId()) {
                stack = ItemStackCodec.decode(claim.itemBase64());
                break;
            }
        }
        Map<String, String> placeholders = Map.of(
                "auction", auctionDisplayName(notification.auctionId()),
                "auction_id", notification.auctionId(),
                "id", String.valueOf(notification.sourceListingId()),
                "claim_id", String.valueOf(notification.claimId())
        );
        Map<String, Component> itemLine = Map.of("item", ItemDisplayNames.chatItem(stack));
        messageService.send(player, "listing-expired-notification", placeholders, itemLine);
        messageService.send(player, "listing-expired-open-hint", placeholders);
    }

    private void notifyListingExpired(AuctionListing listing, ClaimEntry claim) {
        if (claim == null) {
            return;
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        PendingExpiredListingNotification notification = new PendingExpiredListingNotification(
                listing.sellerId(),
                listing.auctionId(),
                listing.listingId(),
                claim.claimId(),
                ItemDisplayNames.plain(item, messageService.javaLocale(listing.sellerId()))
        );
        Player online = Bukkit.getPlayer(listing.sellerId());
        if (online != null && online.isOnline()) {
            deliverExpiredListingNotification(online, notification);
            return;
        }
        runtimeStorage.addPendingExpiredListingNotification(notification);
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
