package bm.b0b0b0.soulAuction.service.fakeactivity;

import bm.b0b0b0.soulAuction.config.FakeActivityConfig;
import bm.b0b0b0.soulAuction.config.FakeActivityConfigLoader;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivitySettings;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.model.result.SellResult;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class FakeActivityService {

    private final JavaPlugin plugin;
    private final AuctionService auctionService;
    private final Supplier<PluginConfig> configSupplier;
    private final Supplier<FakeActivityConfig> fakeConfigSupplier;
    private final FakeActivityConfigLoader fakeActivityConfigLoader;
    private final Runnable reloadFakeActivityConfig;
    private final Random random = new Random();
    private volatile boolean started;

    public FakeActivityService(
            JavaPlugin plugin,
            AuctionService auctionService,
            Supplier<PluginConfig> configSupplier,
            Supplier<FakeActivityConfig> fakeConfigSupplier,
            FakeActivityConfigLoader fakeActivityConfigLoader,
            Runnable reloadFakeActivityConfig
    ) {
        this.plugin = plugin;
        this.auctionService = auctionService;
        this.configSupplier = configSupplier;
        this.fakeConfigSupplier = fakeConfigSupplier;
        this.fakeActivityConfigLoader = fakeActivityConfigLoader;
        this.reloadFakeActivityConfig = reloadFakeActivityConfig;
    }

    public List<String> sellerNames() {
        FakeActivityConfig config = fakeConfigSupplier.get();
        if (config == null || config.sellers().names == null || config.sellers().names.isEmpty()) {
            return List.of();
        }
        return List.copyOf(config.sellers().names);
    }

    public void registerFromAdminFake(String sellerName, ItemStack item, String auctionId) {
        FakeActivityConfig config = fakeConfigSupplier.get();
        if (config == null) {
            return;
        }
        FakeActivitySettings settings = config.settings();
        boolean registerSeller = settings.adminFake.registerSeller;
        boolean registerItem = settings.adminFake.registerItem;
        if (!registerSeller && !registerItem) {
            return;
        }
        if (registerSeller && (sellerName == null || sellerName.isBlank())) {
            registerSeller = false;
        }
        if (registerItem && (item == null || item.isEmpty())) {
            registerItem = false;
        }
        if (!registerSeller && !registerItem) {
            return;
        }
        ItemStack itemSnapshot = registerItem ? item.clone() : null;
        String sellerSnapshot = registerSeller ? sellerName.trim() : null;
        String auctionSnapshot = auctionId == null ? "" : auctionId.trim();
        boolean finalRegisterSeller = registerSeller;
        boolean finalRegisterItem = registerItem;
        PluginSchedulers.runAsync(plugin, () -> {
            try {
                boolean changed = false;
                if (finalRegisterSeller) {
                    changed |= fakeActivityConfigLoader.registerSellerIfAbsent(
                            configSupplier.get().auctionSettings(),
                            sellerSnapshot
                    );
                }
                if (finalRegisterItem) {
                    changed |= fakeActivityConfigLoader.registerItemIfAbsent(
                            configSupplier.get().auctionSettings(),
                            itemSnapshot,
                            auctionSnapshot
                    );
                }
                if (changed) {
                    PluginSchedulers.runGlobal(plugin, reloadFakeActivityConfig);
                }
            } catch (Exception ignored) {
            }
        });
    }

    public void start() {
        if (started) {
            return;
        }
        if (!anyAuctionFakeEnabled()) {
            return;
        }
        started = true;
        bootstrapIfNeeded();
        FakeActivitySettings settings = fakeConfigSupplier.get().settings();
        long initialTicks = Math.max(20L, settings.initialDelaySeconds * 20L);
        long periodTicks = Math.max(20L, settings.tickIntervalSeconds * 20L);
        PluginSchedulers.runGlobalTimer(plugin, initialTicks, periodTicks, this::tick);
    }

    public void reload() {
        if (!anyAuctionFakeEnabled()) {
            started = false;
            return;
        }
        if (!started) {
            start();
            return;
        }
        PluginSchedulers.runGlobal(plugin, this::bootstrapIfNeeded);
    }

    public boolean isFakeEnabledForAuction(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            return false;
        }
        var definition = auctionService.findAuctionDefinition(auctionId);
        return definition != null && definition.fakeActivityEnabled;
    }

    private void bootstrapIfNeeded() {
        if (!anyAuctionFakeEnabled() || !auctionService.isLoaded()) {
            return;
        }
        FakeActivityConfig config = fakeConfigSupplier.get();
        FakeActivitySettings settings = config.settings();
        if (config.sellers().names == null || config.sellers().names.isEmpty()) {
            return;
        }
        if (config.items().isEmpty()) {
            return;
        }
        int target = settings.initialFillListings > 0
                ? Math.min(settings.initialFillListings, settings.maxTotalListings)
                : settings.maxTotalListings;
        int missing = target - auctionService.countSyntheticListings(null);
        if (missing <= 0) {
            return;
        }
        List<String> auctionIds = resolveAuctionIds(settings);
        if (auctionIds.isEmpty()) {
            return;
        }
        int created = 0;
        int attempts = 0;
        int attemptLimit = Math.max(missing * 4, missing + 8);
        while (created < missing
                && attempts < attemptLimit
                && auctionService.countSyntheticListings(null) < settings.maxTotalListings) {
            attempts++;
            String auctionId = pickAuctionId(auctionIds, settings.maxListingsPerAuction);
            if (auctionId == null) {
                break;
            }
            String seller = pickSellerName(config.sellers().names, auctionId);
            if (seller == null) {
                break;
            }
            if (tryCreateListing(auctionId, seller, config)) {
                created++;
            }
        }
    }

    public void onSyntheticPurchased(AuctionListing listing) {
        if (listing == null || !isFakeEnabledForAuction(listing.auctionId())) {
            return;
        }
        FakeActivitySettings settings = fakeConfigSupplier.get().settings();
        long delayTicks = Math.max(20L, settings.afterPurchaseDelaySeconds * 20L);
        String auctionId = listing.auctionId();
        String sellerName = listing.sellerName();
        PluginSchedulers.runGlobalLater(plugin, delayTicks, () -> {
            if (!isFakeEnabledForAuction(auctionId) || !auctionService.isLoaded()) {
                return;
            }
            if (auctionService.countSyntheticListings(null) >= settings.maxTotalListings) {
                return;
            }
            if (auctionService.countSyntheticListings(auctionId) >= settings.maxListingsPerAuction) {
                return;
            }
            createListing(auctionId, sellerName);
        });
    }

    private void tick() {
        if (!anyAuctionFakeEnabled() || !auctionService.isLoaded()) {
            return;
        }
        FakeActivityConfig config = fakeConfigSupplier.get();
        FakeActivitySettings settings = config.settings();
        if (config.sellers().names == null || config.sellers().names.isEmpty()) {
            return;
        }
        if (config.items().isEmpty()) {
            return;
        }
        if (auctionService.countSyntheticListings(null) >= settings.maxTotalListings) {
            return;
        }
        List<String> auctionIds = resolveAuctionIds(settings);
        if (auctionIds.isEmpty()) {
            return;
        }
        int created = 0;
        while (created < settings.listingsPerTick
                && auctionService.countSyntheticListings(null) < settings.maxTotalListings) {
            String auctionId = pickAuctionId(auctionIds, settings.maxListingsPerAuction);
            if (auctionId == null) {
                break;
            }
            String seller = pickSellerName(config.sellers().names, auctionId);
            if (seller == null) {
                break;
            }
            if (tryCreateListing(auctionId, seller, config)) {
                created++;
            }
        }
    }

    private boolean tryCreateListing(String auctionId, String sellerName, FakeActivityConfig config) {
        FakeActivityItemSettings item = pickItem(config.items(), auctionId);
        if (item == null) {
            return false;
        }
        ItemStack stack = FakeActivityItemFactory.build(item);
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        FakeActivitySettings settings = config.settings();
        int price = pickPrice(settings, item);
        long createdAt = resolveListingCreatedAt(settings, auctionId);
        SellResult result = auctionService.createAdminFakeListing(sellerName, auctionId, price, stack, createdAt);
        return result.success();
    }

    private long resolveListingCreatedAt(FakeActivitySettings settings, String auctionId) {
        int spreadSeconds = settings.listingAgeSpreadSeconds;
        if (spreadSeconds <= 0) {
            return System.currentTimeMillis();
        }
        int ttlSeconds = auctionService.listingTtlSeconds(auctionId);
        if (ttlSeconds > 60) {
            spreadSeconds = Math.min(spreadSeconds, ttlSeconds - 60);
        }
        if (spreadSeconds <= 0) {
            return System.currentTimeMillis();
        }
        int backSeconds = random.nextInt(spreadSeconds + 1);
        return System.currentTimeMillis() - backSeconds * 1000L;
    }

    private void createListing(String auctionId, String preferredSeller) {
        FakeActivityConfig config = fakeConfigSupplier.get();
        List<String> sellers = config.sellers().names;
        if (sellers == null || sellers.isEmpty()) {
            return;
        }
        String seller = preferredSeller;
        if (seller == null || seller.isBlank() || auctionService.syntheticSellerHasActiveListing(seller, auctionId)) {
            seller = pickSellerName(sellers, auctionId);
        }
        if (seller == null) {
            seller = sellers.get(random.nextInt(sellers.size())).trim();
        }
        tryCreateListing(auctionId, seller, config);
    }

    private String pickAuctionId(List<String> auctionIds, int maxPerAuction) {
        String best = null;
        int bestCount = Integer.MAX_VALUE;
        for (String auctionId : auctionIds) {
            int count = auctionService.countSyntheticListings(auctionId);
            if (count >= maxPerAuction) {
                continue;
            }
            if (count < bestCount) {
                bestCount = count;
                best = auctionId;
            }
        }
        return best;
    }

    private String pickSellerName(List<String> names, String auctionId) {
        List<String> trimmed = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String value = name.trim();
            if (value.length() > 16) {
                continue;
            }
            trimmed.add(value);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        List<String> free = new ArrayList<>();
        for (String name : trimmed) {
            if (!auctionService.syntheticSellerHasActiveListing(name, auctionId)) {
                free.add(name);
            }
        }
        List<String> pool = free.isEmpty() ? trimmed : free;
        return pool.get(random.nextInt(pool.size()));
    }

    private FakeActivityItemSettings pickItem(List<FakeActivityItemSettings> items, String auctionId) {
        List<FakeActivityItemSettings> eligible = new ArrayList<>();
        int totalWeight = 0;
        for (FakeActivityItemSettings item : items) {
            if (!item.appliesToAuction(auctionId)) {
                continue;
            }
            int weight = Math.max(1, item.weight);
            eligible.add(item);
            totalWeight += weight;
        }
        if (eligible.isEmpty() || totalWeight <= 0) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        int cursor = 0;
        for (FakeActivityItemSettings item : eligible) {
            cursor += Math.max(1, item.weight);
            if (roll < cursor) {
                return item;
            }
        }
        return eligible.get(eligible.size() - 1);
    }

    private int pickPrice(FakeActivitySettings settings, FakeActivityItemSettings item) {
        int min = item.minPrice > 0 ? item.minPrice : settings.minPrice;
        int max = item.maxPrice > 0 ? item.maxPrice : settings.maxPrice;
        if (min > max) {
            int swap = min;
            min = max;
            max = swap;
        }
        if (min == max) {
            return Math.max(1, min);
        }
        int base = min + random.nextInt(max - min + 1);
        int variance = Math.max(0, settings.priceVariancePercent);
        if (variance <= 0) {
            return base;
        }
        int spread = (int) Math.round(base * (variance / 100.0D));
        int low = Math.max(min, base - spread);
        int high = Math.min(max, base + spread);
        if (low >= high) {
            return base;
        }
        return low + random.nextInt(high - low + 1);
    }

    private List<String> resolveAuctionIds(FakeActivitySettings settings) {
        List<String> fakeEnabled = auctionService.sortedAuctionDefinitions().stream()
                .filter(definition -> definition.fakeActivityEnabled)
                .map(definition -> definition.id.toLowerCase(Locale.ROOT))
                .sorted(Comparator.naturalOrder())
                .toList();
        if (fakeEnabled.isEmpty()) {
            return List.of();
        }
        List<String> configured = settings.auctionIds;
        if (configured != null && !configured.isEmpty()) {
            List<String> resolved = new ArrayList<>();
            for (String auctionId : configured) {
                if (auctionId == null || auctionId.isBlank()) {
                    continue;
                }
                String normalized = auctionId.trim().toLowerCase(Locale.ROOT);
                if (auctionService.auctionExists(normalized)
                        && isFakeEnabledForAuction(normalized)
                        && !resolved.contains(normalized)) {
                    resolved.add(normalized);
                }
            }
            return resolved;
        }
        return fakeEnabled;
    }

    private boolean anyAuctionFakeEnabled() {
        return auctionService.sortedAuctionDefinitions().stream()
                .anyMatch(definition -> definition.fakeActivityEnabled);
    }
}
