package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkinRestorerBridge {

    private static final long DEFAULT_WARMUP_DELAY_MILLIS = 100L;

    private final JavaPlugin plugin;
    private volatile Object skinStorage;
    private volatile Object playerStorage;
    private volatile boolean lookupDone;

    public SkinRestorerBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public record WarmupResult(int requested, int resolved) {
    }

    public static boolean isPluginInstalled() {
        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("SkinsRestorer");
        return plugin != null && plugin.isEnabled();
    }

    public boolean enabled() {
        return skinStorage() != null;
    }

    public CompletableFuture<Optional<SkinTexture>> fetchSkinProperty(String playerName) {
        return fetchSkinProperty(playerName, null, null);
    }

    public CompletableFuture<Optional<SkinTexture>> fetchSkinProperty(
            String playerName,
            UUID sellerId,
            String fallbackSkin
    ) {
        if (playerName == null || playerName.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        Object storage = skinStorage();
        if (storage == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        CompletableFuture<Optional<SkinTexture>> future = new CompletableFuture<>();
        String trimmed = playerName.trim();
        String fallback = blankToNull(fallbackSkin);
        PluginSchedulers.runAsync(plugin, () -> future.complete(
                resolveSkinTexture(storage, trimmed, sellerId, fallback)
        ));
        return future;
    }

    public CompletableFuture<WarmupResult> warmup(Collection<String> playerNames) {
        return warmup(playerNames, DEFAULT_WARMUP_DELAY_MILLIS);
    }

    public CompletableFuture<WarmupResult> warmup(Collection<String> playerNames, long delayMillis) {
        Set<String> names = normalizeNames(playerNames);
        if (names.isEmpty()) {
            return CompletableFuture.completedFuture(new WarmupResult(0, 0));
        }
        Object storage = skinStorage();
        if (storage == null) {
            return CompletableFuture.completedFuture(new WarmupResult(0, 0));
        }
        CompletableFuture<WarmupResult> future = new CompletableFuture<>();
        PluginSchedulers.runAsync(plugin, () -> {
            int requested = 0;
            int resolved = 0;
            for (String name : names) {
                requested++;
                if (resolveSkinTexture(storage, name, null, null).isPresent()) {
                    resolved++;
                }
                sleepQuietly(delayMillis);
            }
            future.complete(new WarmupResult(requested, resolved));
        });
        return future;
    }

    public void prefetch(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        Object storage = skinStorage();
        if (storage == null) {
            return;
        }
        String trimmed = playerName.trim();
        PluginSchedulers.runAsync(plugin, () -> resolveSkinTexture(storage, trimmed, null, null));
    }

    private Object skinStorage() {
        ensureLookup();
        return skinStorage;
    }

    private Object playerStorage() {
        ensureLookup();
        return playerStorage;
    }

    private void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (this) {
            if (lookupDone) {
                return;
            }
            Object api = lookupApi();
            if (api != null) {
                skinStorage = invokeNoArg(api, "getSkinStorage");
                playerStorage = invokeNoArg(api, "getPlayerStorage");
            }
            lookupDone = true;
        }
    }

    private static Object lookupApi() {
        if (!isPluginInstalled()) {
            return null;
        }
        try {
            Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Method getMethod = providerClass.getMethod("get");
            return getMethod.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Optional<SkinTexture> resolveSkinTexture(
            Object storage,
            String playerName,
            UUID sellerId,
            String fallbackSkin
    ) {
        Optional<SkinTexture> primary = resolveStoredSkin(storage, playerName);
        if (primary.isPresent()) {
            return primary;
        }
        String fallback = blankToNull(fallbackSkin);
        if (fallback != null) {
            Optional<SkinTexture> fallbackTexture = resolveStoredSkin(storage, fallback);
            if (fallbackTexture.isPresent()) {
                return fallbackTexture;
            }
        }
        if (sellerId != null) {
            Optional<SkinTexture> playerSkin = resolvePlayerSkin(sellerId, playerName);
            if (playerSkin.isPresent()) {
                return playerSkin;
            }
        }
        return Optional.empty();
    }

    private Optional<SkinTexture> resolveStoredSkin(Object storage, String input) {
        try {
            Method findMethod = storage.getClass().getMethod("findSkinData", String.class);
            Object optionalResult = findMethod.invoke(storage, input);
            return optionalToTexture(optionalResult);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private Optional<SkinTexture> resolvePlayerSkin(UUID sellerId, String playerName) {
        Object storage = playerStorage();
        if (storage == null) {
            return Optional.empty();
        }
        try {
            Method method = storage.getClass().getMethod(
                    "getSkinForPlayer",
                    UUID.class,
                    String.class,
                    boolean.class
            );
            Object optionalResult = method.invoke(storage, sellerId, playerName, false);
            return optionalToTexture(optionalResult);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<SkinTexture> optionalToTexture(Object optionalResult) {
        if (!(optionalResult instanceof Optional<?> optional) || optional.isEmpty()) {
            return Optional.empty();
        }
        Object value = optional.get();
        Optional<SkinTexture> fromProperty = readSkinProperty(value);
        if (fromProperty.isPresent()) {
            return fromProperty;
        }
        try {
            Method propertyMethod = value.getClass().getMethod("getProperty");
            Object property = propertyMethod.invoke(value);
            return readSkinProperty(property);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<SkinTexture> readSkinProperty(Object property) {
        if (property == null) {
            return Optional.empty();
        }
        try {
            Method valueMethod = property.getClass().getMethod("getValue");
            Method signatureMethod = property.getClass().getMethod("getSignature");
            Object value = valueMethod.invoke(property);
            Object signature = signatureMethod.invoke(property);
            if (!(value instanceof String valueText) || valueText.isBlank()) {
                return Optional.empty();
            }
            String signatureText = signature instanceof String text ? text : "";
            return Optional.of(new SkinTexture(valueText, signatureText));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Set<String> normalizeNames(Collection<String> playerNames) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (playerNames == null) {
            return names;
        }
        for (String name : playerNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            names.add(name.trim());
        }
        return names;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
