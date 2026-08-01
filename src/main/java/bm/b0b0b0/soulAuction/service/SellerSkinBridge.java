package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.plugin.java.JavaPlugin;

public final class SellerSkinBridge {

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final ConcurrentHashMap<String, CompletableFuture<Optional<SkinTexture>>> inFlight = new ConcurrentHashMap<>();
    private volatile SkinRestorerBridge skinRestorerBridge;

    public SellerSkinBridge(JavaPlugin plugin, Supplier<PluginConfig> configSupplier) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
    }

    public SellerSkinSource configuredSource() {
        if (configSupplier.get() == null || configSupplier.get().auctionSettings() == null) {
            return SellerSkinSource.AUTO;
        }
        return SellerSkinSource.parse(configSupplier.get().auctionSettings().sellerSkins.source);
    }

    public boolean enabled() {
        return configuredSource() != SellerSkinSource.OFF;
    }

    public boolean usesSkinsRestorerWarmup() {
        SellerSkinSource source = configuredSource();
        if (source == SellerSkinSource.OFF || source == SellerSkinSource.MOJANG) {
            return false;
        }
        if (source == SellerSkinSource.SKINSRESTORER) {
            return skinRestorerBridge().enabled();
        }
        return skinRestorerBridge().enabled();
    }

    public CompletableFuture<Optional<SkinTexture>> fetchSkinProperty(String playerName) {
        return fetchSkinProperty(playerName, null, false);
    }

    public CompletableFuture<Optional<SkinTexture>> fetchSkinProperty(
            String playerName,
            UUID sellerId,
            boolean syntheticSeller
    ) {
        if (playerName == null || playerName.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (!enabled()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        AuctionSettings.SellerSkinSettings settings = sellerSettings();
        String lookupName = playerName.trim();
        boolean resolvedSynthetic = syntheticSeller;
        if (syntheticSeller) {
            String forced = blankToNull(settings.fakeSellerSkin);
            if (forced != null) {
                lookupName = forced;
                resolvedSynthetic = false;
            }
        }
        String key = inFlightKey(lookupName, sellerId, resolvedSynthetic, settings);
        String resolvedLookupName = lookupName;
        boolean remoteSynthetic = resolvedSynthetic;
        return inFlight.computeIfAbsent(key, unused -> resolveRemote(
                resolvedLookupName,
                sellerId,
                remoteSynthetic,
                settings
        ).whenComplete((result, error) -> inFlight.remove(key)));
    }

    public CompletableFuture<SkinRestorerBridge.WarmupResult> warmupSkinsRestorerCache(Collection<String> playerNames) {
        if (!usesSkinsRestorerWarmup()) {
            return CompletableFuture.completedFuture(new SkinRestorerBridge.WarmupResult(0, 0));
        }
        return skinRestorerBridge().warmup(playerNames);
    }

    public void prefetchSkinsRestorer(String playerName) {
        if (!usesSkinsRestorerWarmup() || playerName == null || playerName.isBlank()) {
            return;
        }
        skinRestorerBridge().prefetch(playerName.trim());
    }

    private CompletableFuture<Optional<SkinTexture>> resolveRemote(
            String playerName,
            UUID sellerId,
            boolean syntheticSeller,
            AuctionSettings.SellerSkinSettings settings
    ) {
        SellerSkinSource source = configuredSource();
        String fallback = blankToNull(settings.fallbackSkin);
        if (source == SellerSkinSource.MOJANG) {
            return fetchMojangWithFallback(playerName, fallback);
        }
        if (source == SellerSkinSource.SKINSRESTORER) {
            return skinRestorerBridge().fetchSkinProperty(playerName, sellerId, fallback);
        }
        if (skinRestorerBridge().enabled()) {
            return skinRestorerBridge().fetchSkinProperty(playerName, sellerId, fallback).thenCompose(result -> {
                if (result.isPresent()) {
                    return CompletableFuture.completedFuture(result);
                }
                return fetchMojangWithFallback(playerName, fallback);
            });
        }
        return fetchMojangWithFallback(playerName, fallback);
    }

    private CompletableFuture<Optional<SkinTexture>> fetchMojangWithFallback(String playerName, String fallbackSkin) {
        CompletableFuture<Optional<SkinTexture>> future = new CompletableFuture<>();
        PluginSchedulers.runAsync(plugin, () -> {
            Optional<SkinTexture> primary = MojangSkinBridge.fetch(playerName);
            if (primary.isPresent()) {
                future.complete(primary);
                return;
            }
            if (fallbackSkin != null) {
                future.complete(MojangSkinBridge.fetch(fallbackSkin));
                return;
            }
            future.complete(Optional.empty());
        });
        return future;
    }

    private AuctionSettings.SellerSkinSettings sellerSettings() {
        PluginConfig config = configSupplier.get();
        if (config == null || config.auctionSettings() == null) {
            return new AuctionSettings.SellerSkinSettings();
        }
        return config.auctionSettings().sellerSkins;
    }

    private SkinRestorerBridge skinRestorerBridge() {
        SkinRestorerBridge bridge = skinRestorerBridge;
        if (bridge != null) {
            return bridge;
        }
        synchronized (this) {
            bridge = skinRestorerBridge;
            if (bridge == null) {
                skinRestorerBridge = bridge = new SkinRestorerBridge(plugin);
            }
            return bridge;
        }
    }

    private static String inFlightKey(
            String playerName,
            UUID sellerId,
            boolean syntheticSeller,
            AuctionSettings.SellerSkinSettings settings
    ) {
        String forced = blankToNull(settings.fakeSellerSkin);
        if (forced != null && syntheticSeller) {
            return "forced:" + forced.toLowerCase(Locale.ROOT);
        }
        String base = playerName.trim().toLowerCase(Locale.ROOT);
        if (sellerId == null) {
            return base;
        }
        return base + ":" + sellerId;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
