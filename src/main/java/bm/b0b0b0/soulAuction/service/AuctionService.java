package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.AuctionSort;
import bm.b0b0b0.soulAuction.model.ClaimEntry;
import bm.b0b0b0.soulAuction.model.DealHistoryEntry;
import bm.b0b0b0.soulAuction.model.PendingSaleNotification;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import bm.b0b0b0.soulAuction.util.ListingSearchText;
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
    private final EconomyBridge vaultBridge;
    private final PlayerPointsBridge playerPointsBridge;
    private final PermissionLimitResolver permissionLimitResolver;
    private final RedisSellGuard redisSellGuard;
    private final AuctionRuntimeStorage runtimeStorage;
    private final TaxPolicyResolver taxPolicyResolver;
    private final PriceLimitResolver priceLimitResolver;
    private final ConcurrentHashMap<UUID, BrowsePreferences> browsePreferences;
    private final ConcurrentHashMap<UUID, Object> playerSellLocks;
    private final ConcurrentHashMap<Long, Object> listingPurchaseLocks;

    public AuctionService(
            AuctionRepository repository,
            Supplier<PluginConfig> configSupplier,
            EconomyBridge vaultBridge,
            PlayerPointsBridge playerPointsBridge,
            PermissionLimitResolver permissionLimitResolver,
            RedisSellGuard redisSellGuard,
            AuctionRuntimeStorage runtimeStorage,
            TaxPolicyResolver taxPolicyResolver,
            PriceLimitResolver priceLimitResolver
    ) {
        this.repository = repository;
        this.configSupplier = configSupplier;
        this.vaultBridge = vaultBridge;
        this.playerPointsBridge = playerPointsBridge;
        this.permissionLimitResolver = permissionLimitResolver;
        this.redisSellGuard = redisSellGuard;
        this.runtimeStorage = runtimeStorage;
        this.taxPolicyResolver = taxPolicyResolver;
        this.priceLimitResolver = priceLimitResolver;
        this.browsePreferences = new ConcurrentHashMap<>();
        this.playerSellLocks = new ConcurrentHashMap<>();
        this.listingPurchaseLocks = new ConcurrentHashMap<>();
    }

    public record BrowsePage(int total, List<AuctionListing> listings) {
    }

    public record BrowsePreferences(String auctionId, int page, String searchQuery) {
    }

    public void setBrowsePreferences(UUID playerId, BrowsePreferences preferences) {
        if (preferences == null) {
            browsePreferences.remove(playerId);
            return;
        }
        browsePreferences.put(playerId, preferences);
    }

    public Optional<BrowsePreferences> consumeBrowsePreferences(UUID playerId) {
        BrowsePreferences preferences = browsePreferences.remove(playerId);
        return Optional.ofNullable(preferences);
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
            SellResult result = createListingInternal(seller, auctionId, price, soldItem);
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
            return createListingInternal(seller, auctionId, price, soldItem);
        }
    }

    private SellResult createListingInternal(Player seller, String auctionId, int price, ItemStack soldItem) {
        AuctionSettings settings = settings();
        if (!settings.limits.allowSelling) {
            return SellResult.failure(SellFailure.SELL_DISABLED);
        }
        if (!redisSellGuard.tryAcquireSellLock(seller.getUniqueId())) {
            return SellResult.failure(SellFailure.SELL_LOCK_FAILED);
        }
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null) {
            return SellResult.failure(SellFailure.AUCTION_NOT_FOUND);
        }
        if (!definition.sellEnabled) {
            return SellResult.failure(SellFailure.SELL_DISABLED_IN_AUCTION);
        }
        if (!hasPermission(seller, definition.sellPermission)) {
            return SellResult.failure(SellFailure.SELL_PERMISSION_DENIED);
        }
        if (!isEconomyAvailable(definition.economy)) {
            return SellResult.failure(SellFailure.ECONOMY_UNAVAILABLE);
        }
        PriceLimitResolver.PriceBounds bounds = priceLimitResolver.resolve(seller, definition, settings.limits);
        if (!bounds.isValid(price)) {
            if (price < bounds.minPrice()) {
                return SellResult.failure(SellFailure.PRICE_TOO_LOW);
            }
            return SellResult.failure(SellFailure.PRICE_TOO_HIGH);
        }
        String normalizedAuctionId = definition.id.toLowerCase(Locale.ROOT);
        int auctionLimit = resolveEffectiveAuctionLimit(seller, normalizedAuctionId);
        int globalLimit = resolveEffectiveGlobalLimit(seller);
        int activeInAuction = repository.countBySellerInAuction(seller.getUniqueId(), normalizedAuctionId);
        if (activeInAuction >= auctionLimit) {
            return SellResult.failure(SellFailure.AUCTION_LIMIT_REACHED);
        }
        int activeGlobal = repository.countBySeller(seller.getUniqueId());
        if (activeGlobal >= globalLimit) {
            return SellResult.failure(SellFailure.GLOBAL_LIMIT_REACHED);
        }
        if (isBlockedMaterial(soldItem.getType(), definition.blockedMaterials)) {
            return SellResult.failure(SellFailure.BLOCKED_ITEM);
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
        return SellResult.success(listing);
    }

    public PurchaseResult purchase(Player buyer, long listingId) {
        if (!redisSellGuard.tryAcquireListingLock(listingId)) {
            return PurchaseResult.failure(PurchaseFailure.LISTING_UNAVAILABLE);
        }
        Object purchaseLock = listingPurchaseLocks.computeIfAbsent(listingId, ignored -> new Object());
        synchronized (purchaseLock) {
            return purchaseLocked(buyer, listingId);
        }
    }

    private PurchaseResult purchaseLocked(Player buyer, long listingId) {
        AuctionSettings settings = settings();
        AuctionListing listing = repository.remove(listingId);
        if (listing == null) {
            return PurchaseResult.failure(PurchaseFailure.LISTING_UNAVAILABLE);
        }
        AuctionDefinitionSettings definition = findAuction(listing.auctionId(), configSupplier.get());
        if (definition == null) {
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.AUCTION_NOT_FOUND);
        }
        if (!definition.buyEnabled) {
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.BUY_DISABLED_IN_AUCTION);
        }
        if (!hasPermission(buyer, definition.buyPermission)) {
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.BUY_PERMISSION_DENIED);
        }
        if (!isEconomyAvailable(listing.economyType())) {
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.ECONOMY_UNAVAILABLE);
        }
        if (!settings.limits.allowSelfBuy && listing.sellerId().equals(buyer.getUniqueId())) {
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.OWN_LISTING);
        }
        Player sellerOnline = Bukkit.getPlayer(listing.sellerId());
        TaxPolicyResolver.TaxAmounts taxes = taxPolicyResolver.resolve(buyer, sellerOnline, definition, listing.price());
        int charge = taxes.buyerCharge(listing.price());
        if (!hasBalance(buyer.getUniqueId(), charge, listing.economyType())) {
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.NOT_ENOUGH_MONEY);
        }
        if (!withdrawBalance(buyer.getUniqueId(), charge, listing.economyType())) {
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.NOT_ENOUGH_MONEY);
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        Map<Integer, ItemStack> leftovers = buyer.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            depositBalance(buyer.getUniqueId(), charge, listing.economyType());
            repository.putBack(listing);
            return PurchaseResult.failure(PurchaseFailure.INVENTORY_FULL);
        }
        int saleTax = taxes.saleTax();
        int buyTax = taxes.buyTax();
        int payout = taxes.sellerPayout(listing.price());
        depositBalance(listing.sellerId(), payout, listing.economyType());
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
        Player seller = sellerOnline;
        if (seller == null) {
            runtimeStorage.addPendingSaleNotification(new PendingSaleNotification(
                    listing.sellerId(),
                    listing.auctionId(),
                    payout,
                    saleTax,
                    listing.economyType()
            ));
        }
        repository.flush();
        return PurchaseResult.success(listing, seller, payout, saleTax, buyTax, charge);
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
            long age = now - listing.createdAtEpochMillis();
            if (age < ttlMillis) {
                continue;
            }
            AuctionListing removed = repository.remove(listing.listingId());
            if (removed == null) {
                continue;
            }
            runtimeStorage.addClaim(
                    removed.sellerId(),
                    removed.auctionId(),
                    removed.listingId(),
                    removed.itemBase64(),
                    "EXPIRED"
            );
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
            expired++;
        }
        if (expired > 0) {
            repository.flush();
        }
        return expired;
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
        AuctionListing listing = repository.remove(listingId);
        if (listing == null) {
            return CancelResult.failure(CancelFailure.NOT_FOUND);
        }
        if (!canCancelAny && !listing.sellerId().equals(seller.getUniqueId())) {
            repository.putBack(listing);
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
            repository.flush();
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
        repository.flush();
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
        String normalizedAuctionId = auctionId.toLowerCase(Locale.ROOT);
        int byPermission = permissionLimitResolver.resolveAuctionLimit(
                player,
                normalizedAuctionId,
                settings().limits.defaultMaxActiveListingsPerAuction
        );
        int byCommand = runtimeStorage.getLimitOverride(player.getUniqueId(), normalizedAuctionId);
        return Math.max(byPermission, byCommand);
    }

    public int resolveEffectiveGlobalLimit(Player player) {
        int byPermission = permissionLimitResolver.resolveGlobalLimit(
                player,
                settings().limits.defaultMaxActiveListingsGlobal
        );
        int byCommand = runtimeStorage.getLimitOverride(player.getUniqueId(), "all");
        return Math.max(byPermission, byCommand);
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
        boolean updated = repository.updatePrice(listingId, newPrice);
        if (!updated) {
            return EditPriceResult.LISTING_UNAVAILABLE;
        }
        repository.flush();
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

    public BrowsePage browsePage(String auctionId, AuctionSort sort, AuctionCategory category, int page, int pageSize, String searchQuery) {
        String normalizedQuery = normalizeSearch(searchQuery);
        List<AuctionListing> filtered = new ArrayList<>();
        for (AuctionListing listing : repository.listByAuction(auctionId)) {
            if (category != AuctionCategory.ALL && listing.category() != category) {
                continue;
            }
            if (normalizedQuery != null && !matchesSearch(listing, normalizedQuery)) {
                continue;
            }
            filtered.add(listing);
        }
        filtered.sort(comparator(sort));
        int offset = Math.max(0, page) * pageSize;
        if (offset >= filtered.size()) {
            return new BrowsePage(filtered.size(), List.of());
        }
        int end = Math.min(filtered.size(), offset + pageSize);
        return new BrowsePage(filtered.size(), filtered.subList(offset, end));
    }

    public List<AuctionListing> page(String auctionId, AuctionSort sort, AuctionCategory category, int page, int pageSize, String searchQuery) {
        return browsePage(auctionId, sort, category, page, pageSize, searchQuery).listings();
    }

    public int count(String auctionId, AuctionCategory category, String searchQuery) {
        return browsePage(auctionId, AuctionSort.NEWEST, category, 0, 1, searchQuery).total();
    }

    private boolean matchesSearch(AuctionListing listing, String query) {
        if (Long.toString(listing.listingId()).contains(query)) {
            return true;
        }
        return ListingSearchText.resolve(listing).contains(query);
    }

    private String normalizeSearch(String searchQuery) {
        if (searchQuery == null || searchQuery.isBlank()) {
            return null;
        }
        return searchQuery.trim().toLowerCase(Locale.ROOT);
    }

    public String formatPrice(int price, AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.format(price);
            case PLAYER_POINTS -> playerPointsBridge.format(price);
        };
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
        if (definition == null) {
            return auctionId;
        }
        return definition.displayName;
    }

    public int expireCheckIntervalSeconds() {
        return Math.max(5, settings().limits.expireCheckIntervalSeconds);
    }

    public boolean hasVault() {
        return vaultBridge.available();
    }

    public boolean hasPlayerPoints() {
        return playerPointsBridge.available();
    }

    public AuctionEconomyType economyType(String auctionId) {
        AuctionDefinitionSettings definition = findAuction(auctionId, configSupplier.get());
        if (definition == null) {
            return AuctionEconomyType.VAULT;
        }
        return AuctionEconomyType.fromString(definition.economy);
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

    /** @deprecated use {@link #maxPrice(Player, String)} */
    @Deprecated
    public int maxPrice() {
        return settings().limits.maxPrice;
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

    private AuctionSettings settings() {
        return configSupplier.get().auctionSettings();
    }

    private Comparator<AuctionListing> comparator(AuctionSort sort) {
        return switch (sort) {
            case NEWEST -> Comparator.comparingLong(AuctionListing::createdAtEpochMillis).reversed();
            case OLDEST -> Comparator.comparingLong(AuctionListing::createdAtEpochMillis);
            case PRICE_ASC -> Comparator.comparingInt(AuctionListing::price);
            case PRICE_DESC -> Comparator.comparingInt(AuctionListing::price).reversed();
            case SELLER_ASC -> Comparator.comparing(AuctionListing::sellerName, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private boolean hasPermission(Player player, String permission) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }

    private boolean isEconomyAvailable(String economyName) {
        return isEconomyAvailable(AuctionEconomyType.fromString(economyName));
    }

    private boolean isEconomyAvailable(AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.available();
            case PLAYER_POINTS -> playerPointsBridge.available();
        };
    }

    private boolean hasBalance(UUID playerId, int amount, AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.has(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.has(playerId, amount);
        };
    }

    private boolean withdrawBalance(UUID playerId, int amount, AuctionEconomyType type) {
        return switch (type) {
            case VAULT -> vaultBridge.withdraw(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.withdraw(playerId, amount);
        };
    }

    private void depositBalance(UUID playerId, int amount, AuctionEconomyType type) {
        switch (type) {
            case VAULT -> vaultBridge.deposit(playerId, amount);
            case PLAYER_POINTS -> playerPointsBridge.deposit(playerId, amount);
        }
    }

    private boolean isBlockedMaterial(Material material, List<String> blockedMaterials) {
        if (blockedMaterials == null || blockedMaterials.isEmpty()) {
            return false;
        }
        String materialName = material.name();
        for (String blocked : blockedMaterials) {
            if (blocked != null && materialName.equalsIgnoreCase(blocked)) {
                return true;
            }
        }
        return false;
    }

    public record SellResult(boolean success, SellFailure failure, AuctionListing listing) {
        public static SellResult success(AuctionListing listing) {
            return new SellResult(true, null, listing);
        }

        public static SellResult failure(SellFailure failure) {
            return new SellResult(false, failure, null);
        }
    }

    public enum SellFailure {
        SELL_DISABLED,
        SELL_LOCK_FAILED,
        SELL_DISABLED_IN_AUCTION,
        SELL_PERMISSION_DENIED,
        AUCTION_NOT_FOUND,
        ECONOMY_UNAVAILABLE,
        INVALID_PRICE,
        AUCTION_LIMIT_REACHED,
        GLOBAL_LIMIT_REACHED,
        BLOCKED_ITEM,
        EMPTY_HAND,
        PRICE_TOO_LOW,
        PRICE_TOO_HIGH
    }

    public record PurchaseQuote(AuctionListing listing, int totalCharge, int saleTax, int buyTax) {
    }

    public record PurchaseResult(
            boolean success,
            PurchaseFailure failure,
            AuctionListing listing,
            Player seller,
            int sellerPayout,
            int tax,
            int buyTax,
            int buyerCharge
    ) {
        public static PurchaseResult success(
                AuctionListing listing,
                Player seller,
                int sellerPayout,
                int tax,
                int buyTax,
                int buyerCharge
        ) {
            return new PurchaseResult(true, null, listing, seller, sellerPayout, tax, buyTax, buyerCharge);
        }

        public static PurchaseResult failure(PurchaseFailure failure) {
            return new PurchaseResult(false, failure, null, null, 0, 0, 0, 0);
        }
    }

    public enum PurchaseFailure {
        LISTING_UNAVAILABLE,
        AUCTION_NOT_FOUND,
        BUY_DISABLED_IN_AUCTION,
        BUY_PERMISSION_DENIED,
        ECONOMY_UNAVAILABLE,
        OWN_LISTING,
        NOT_ENOUGH_MONEY,
        INVENTORY_FULL
    }

    public record ClaimResult(int claimed, int failed) {
    }

    public record CancelResult(boolean success, boolean movedToClaim, CancelFailure failure) {
        public static CancelResult success(boolean movedToClaim) {
            return new CancelResult(true, movedToClaim, null);
        }

        public static CancelResult failure(CancelFailure failure) {
            return new CancelResult(false, false, failure);
        }
    }

    public enum CancelFailure {
        NOT_FOUND,
        NOT_OWNER
    }

    public enum EditPriceResult {
        SUCCESS,
        LISTING_UNAVAILABLE,
        NOT_OWNER,
        INVALID_PRICE
    }
}
