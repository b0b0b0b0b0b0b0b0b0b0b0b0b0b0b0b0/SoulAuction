package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DataLayout {

    public static final String ROOT = "data";
    public static final String DATABASE_DIR = "data/database";
    public static final String LISTINGS_DIR = "data/listings";
    public static final String PLAYERS_DIR = "data/players";
    public static final String RECORDS_DIR = "data/records";
    public static final String RUNTIME_DIR = "data/runtime";

    private static final String LEGACY_SQLITE = "data/auction.db";

    private DataLayout() {
    }

    public static Path root(Path pluginRoot) {
        return pluginRoot.resolve(ROOT);
    }

    public static Path databaseDirectory(Path pluginRoot) {
        return pluginRoot.resolve(DATABASE_DIR);
    }

    public static Path sqliteFile(Path pluginRoot) {
        return databaseDirectory(pluginRoot).resolve("auction.db");
    }

    public static Path storageRuntimeMeta(Path pluginRoot) {
        return pluginRoot.resolve(RUNTIME_DIR).resolve("storage-runtime.yml");
    }

    public static Path playersFile(Path pluginRoot, String name) {
        return pluginRoot.resolve(PLAYERS_DIR).resolve(name);
    }

    public static Path recordsFile(Path pluginRoot, String name) {
        return pluginRoot.resolve(RECORDS_DIR).resolve(name);
    }

    public static Path runtimeFile(Path pluginRoot, String name) {
        return pluginRoot.resolve(RUNTIME_DIR).resolve(name);
    }

    public static void ensureDirectories(Path pluginRoot) throws IOException {
        Files.createDirectories(databaseDirectory(pluginRoot));
        Files.createDirectories(pluginRoot.resolve(LISTINGS_DIR));
        Files.createDirectories(pluginRoot.resolve(PLAYERS_DIR));
        Files.createDirectories(pluginRoot.resolve(RECORDS_DIR));
        Files.createDirectories(pluginRoot.resolve(RUNTIME_DIR));
    }

    public static void migrateLegacyLayout(Path pluginRoot, AuctionSettings settings) throws IOException {
        ensureDirectories(pluginRoot);
        Path dataRoot = root(pluginRoot);
        if (!Files.isDirectory(dataRoot)) {
            return;
        }
        moveIfNeeded(dataRoot.resolve("auction.db"), sqliteFile(pluginRoot));
        moveIfNeeded(dataRoot.resolve("storage-runtime.yml"), storageRuntimeMeta(pluginRoot));
        moveIfNeeded(dataRoot.resolve("claims.json"), recordsFile(pluginRoot, "claims.json"));
        moveIfNeeded(dataRoot.resolve("history.json"), recordsFile(pluginRoot, "history.json"));
        moveIfNeeded(dataRoot.resolve("audit.json"), recordsFile(pluginRoot, "audit.json"));
        moveIfNeeded(dataRoot.resolve("favorites.json"), playersFile(pluginRoot, "favorites.json"));
        moveIfNeeded(dataRoot.resolve("favorite-listings.json"), playersFile(pluginRoot, "favorite-listings.json"));
        moveIfNeeded(dataRoot.resolve("limits.json"), playersFile(pluginRoot, "limits.json"));
        moveIfNeeded(dataRoot.resolve("notifications.json"), playersFile(pluginRoot, "notifications.json"));
        moveIfNeeded(dataRoot.resolve("sell-cooldowns.json"), playersFile(pluginRoot, "sell-cooldowns.json"));
        moveIfNeeded(dataRoot.resolve("stats.json"), playersFile(pluginRoot, "stats.json"));
        moveIfNeeded(dataRoot.resolve("runtime-blacklist.json"), runtimeFile(pluginRoot, "runtime-blacklist.json"));
        moveIfNeeded(dataRoot.resolve("synthetic-sellers.json"), runtimeFile(pluginRoot, "synthetic-sellers.json"));
        syncSqliteSetting(pluginRoot, settings);
    }

    public static Path resolveSqliteFile(Path pluginRoot, String configuredPath) {
        Path configured = pluginRoot.resolve(configuredPath == null ? "" : configuredPath.trim());
        if (Files.isRegularFile(configured)) {
            return configured;
        }
        Path canonical = sqliteFile(pluginRoot);
        if (Files.isRegularFile(canonical)) {
            return canonical;
        }
        Path legacy = pluginRoot.resolve(LEGACY_SQLITE);
        if (Files.isRegularFile(legacy)) {
            return legacy;
        }
        return configuredPath == null || configuredPath.isBlank() ? canonical : configured;
    }

    private static void syncSqliteSetting(Path pluginRoot, AuctionSettings settings) {
        if (settings == null || settings.storage == null || settings.storage.database == null) {
            return;
        }
        String configured = settings.storage.database.sqliteFile == null
                ? ""
                : settings.storage.database.sqliteFile.trim();
        if (!LEGACY_SQLITE.equals(configured) && !configured.isEmpty()) {
            return;
        }
        if (!Files.isRegularFile(sqliteFile(pluginRoot))) {
            return;
        }
        settings.storage.database.sqliteFile = relativePath(pluginRoot, sqliteFile(pluginRoot));
    }

    private static void moveIfNeeded(Path legacy, Path target) throws IOException {
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        if (Files.isRegularFile(target)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.move(legacy, target);
    }

    private static String relativePath(Path pluginRoot, Path absolute) {
        return pluginRoot.relativize(absolute).toString().replace('\\', '/');
    }
}
