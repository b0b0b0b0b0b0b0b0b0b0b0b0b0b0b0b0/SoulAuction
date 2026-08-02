package bm.b0b0b0.soulAuction.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Locale;
import org.bukkit.plugin.java.JavaPlugin;

public final class MinecraftLangAssetFetcher {

    private static final URI MANIFEST_URI = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final URI RESOURCE_BASE = URI.create("https://resources.download.minecraft.net/");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MinecraftLangAssetFetcher() {
    }

    public static boolean ensureCached(JavaPlugin plugin, String minecraftVersion, Locale[] locales) {
        if (plugin == null || minecraftVersion == null || minecraftVersion.isBlank() || locales == null) {
            return false;
        }
        LinkedHashSet<String> fileNames = new LinkedHashSet<>();
        for (Locale locale : locales) {
            if (locale == null) {
                continue;
            }
            fileNames.addAll(MinecraftLangCatalog.fileNamesFor(locale));
        }
        if (fileNames.isEmpty()) {
            return false;
        }
        Path cacheRoot = plugin.getDataFolder().toPath().resolve("lang-cache");
        Path versionDir = cacheRoot.resolve(sanitizeVersion(minecraftVersion));
        boolean downloaded = false;
        for (String fileName : fileNames) {
            Path target = versionDir.resolve(fileName);
            if (Files.isRegularFile(target)) {
                continue;
            }
            if (downloadLangFile(minecraftVersion, fileName, target)) {
                downloaded = true;
            }
        }
        return downloaded;
    }

    private static String sanitizeVersion(String minecraftVersion) {
        return minecraftVersion.trim().replace('/', '_');
    }

    private static boolean downloadLangFile(String minecraftVersion, String fileName, Path target) {
        try {
            String assetPath = "minecraft/lang/" + fileName;
            String hash = resolveAssetHash(minecraftVersion, assetPath);
            if (hash == null || hash.isBlank()) {
                return false;
            }
            URI resourceUri = RESOURCE_BASE.resolve(hash.substring(0, 2) + "/" + hash);
            HttpRequest request = HttpRequest.newBuilder(resourceUri)
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "lang-", ".tmp");
            try (InputStream body = response.body()) {
                Files.copy(body, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String resolveAssetHash(String minecraftVersion, String assetPath) throws Exception {
        String versionJsonUrl = resolveVersionJsonUrl(minecraftVersion);
        if (versionJsonUrl == null) {
            return null;
        }
        JsonObject versionJson = fetchJson(URI.create(versionJsonUrl));
        if (versionJson == null || !versionJson.has("assetIndex")) {
            return null;
        }
        JsonObject assetIndex = versionJson.getAsJsonObject("assetIndex");
        if (assetIndex == null || !assetIndex.has("url")) {
            return null;
        }
        JsonObject indexJson = fetchJson(URI.create(assetIndex.get("url").getAsString()));
        if (indexJson == null || !indexJson.has("objects")) {
            return null;
        }
        JsonObject objects = indexJson.getAsJsonObject("objects");
        if (objects == null || !objects.has(assetPath)) {
            return null;
        }
        JsonObject entry = objects.getAsJsonObject(assetPath);
        if (entry == null || !entry.has("hash")) {
            return null;
        }
        return entry.get("hash").getAsString();
    }

    private static String resolveVersionJsonUrl(String minecraftVersion) throws Exception {
        JsonObject manifest = fetchJson(MANIFEST_URI);
        if (manifest == null || !manifest.has("versions")) {
            return null;
        }
        JsonArray versions = manifest.getAsJsonArray("versions");
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject version = element.getAsJsonObject();
            if (!version.has("id") || !version.has("url")) {
                continue;
            }
            if (minecraftVersion.equalsIgnoreCase(version.get("id").getAsString())) {
                return version.get("url").getAsString();
            }
        }
        return null;
    }

    private static JsonObject fetchJson(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(45))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return null;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
}
