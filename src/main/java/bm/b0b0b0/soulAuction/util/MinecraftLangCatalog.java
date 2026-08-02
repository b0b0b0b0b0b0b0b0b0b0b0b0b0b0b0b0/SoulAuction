package bm.b0b0b0.soulAuction.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.bukkit.Bukkit;

public final class MinecraftLangCatalog {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {
    }.getType();
    private static final ConcurrentHashMap<String, Map<String, String>> CATALOGS = new ConcurrentHashMap<>();
    private static volatile List<Path> serverJarCandidates;
    private static volatile Path pluginCacheRoot;
    private static volatile String pluginCacheVersion;

    private MinecraftLangCatalog() {
    }

    public static void configure(Path cacheRoot, String minecraftVersion) {
        pluginCacheRoot = cacheRoot;
        pluginCacheVersion = minecraftVersion == null ? null : minecraftVersion.trim();
        clear();
    }

    public static List<String> fileNamesFor(Locale locale) {
        return candidateFileNames(locale);
    }

    public static String translate(String translationKey, Locale locale) {
        if (translationKey == null || translationKey.isBlank()) {
            return null;
        }
        Map<String, String> catalog = catalogFor(locale);
        if (catalog.isEmpty()) {
            return null;
        }
        String value = catalog.get(translationKey);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public static void clear() {
        CATALOGS.clear();
        serverJarCandidates = null;
    }

    private static Map<String, String> catalogFor(Locale locale) {
        Locale normalized = SearchLocales.normalize(locale);
        String cacheKey = normalized.toLanguageTag();
        return CATALOGS.computeIfAbsent(cacheKey, ignored -> loadFirstCatalog(normalized));
    }

    private static Map<String, String> loadFirstCatalog(Locale locale) {
        for (String fileName : candidateFileNames(locale)) {
            Map<String, String> loaded = loadCatalog(fileName);
            if (!loaded.isEmpty()) {
                return loaded;
            }
        }
        return Map.of();
    }

    private static List<String> candidateFileNames(Locale locale) {
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        if (language.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        String country = locale.getCountry();
        if (country != null && !country.isBlank()) {
            names.add(language + "_" + country.toLowerCase(Locale.ROOT) + ".json");
        }
        names.add(language + "_" + language + ".json");
        switch (language) {
            case "en" -> {
                names.add("en_us.json");
                names.add("en_gb.json");
            }
            case "ru" -> names.add("ru_ru.json");
            case "uk" -> names.add("uk_ua.json");
            case "de" -> names.add("de_de.json");
            case "pl" -> names.add("pl_pl.json");
            default -> {
            }
        }
        return List.copyOf(names);
    }

    private static Map<String, String> loadCatalog(String fileName) {
        Map<String, String> fromPluginCache = loadFromPluginCache(fileName);
        if (!fromPluginCache.isEmpty()) {
            return fromPluginCache;
        }
        Map<String, String> fromClasspath = loadFromClasspath(fileName);
        if (!fromClasspath.isEmpty()) {
            return fromClasspath;
        }
        return loadFromServerJars(fileName);
    }

    private static Map<String, String> loadFromPluginCache(String fileName) {
        Path root = pluginCacheRoot;
        String version = pluginCacheVersion;
        if (root == null || version == null || version.isBlank() || fileName == null || fileName.isBlank()) {
            return Map.of();
        }
        Path file = root.resolve(version.replace('/', '_')).resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try (InputStream stream = Files.newInputStream(file)) {
            return parseStream(stream);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Map<String, String> loadFromClasspath(String fileName) {
        String resourcePath = "assets/minecraft/lang/" + fileName;
        for (ClassLoader loader : classLoaders()) {
            if (loader == null) {
                continue;
            }
            try (InputStream stream = loader.getResourceAsStream(resourcePath)) {
                if (stream == null) {
                    continue;
                }
                Map<String, String> parsed = parseStream(stream);
                if (!parsed.isEmpty()) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        return Map.of();
    }

    private static Map<String, String> loadFromServerJars(String fileName) {
        String entryPath = "assets/minecraft/lang/" + fileName;
        for (Path jarPath : serverJarCandidates()) {
            Map<String, String> parsed = loadCatalogFromJar(jarPath, entryPath);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        return Map.of();
    }

    private static Map<String, String> loadCatalogFromJar(Path jarPath, String entryPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return Map.of();
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(entryPath);
            if (entry == null) {
                return Map.of();
            }
            try (InputStream stream = jar.getInputStream(entry)) {
                return parseStream(stream);
            }
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static List<Path> serverJarCandidates() {
        List<Path> cached = serverJarCandidates;
        if (cached != null) {
            return cached;
        }
        LinkedHashSet<Path> jars = new LinkedHashSet<>();
        Path codeSource = serverCodeSourceJar();
        if (codeSource != null) {
            jars.add(codeSource);
        }
        if (Bukkit.getServer() != null) {
            Path serverRoot = Bukkit.getServer().getPluginsFolder().toPath().getParent();
            if (serverRoot != null) {
                Path versions = serverRoot.resolve("versions");
                if (Files.isDirectory(versions)) {
                    try (Stream<Path> walk = Files.walk(versions, 4)) {
                        walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                                .forEach(jars::add);
                    } catch (Exception ignored) {
                    }
                }
                Path libraries = serverRoot.resolve("libraries");
                if (Files.isDirectory(libraries)) {
                    try (Stream<Path> walk = Files.walk(libraries, 10)) {
                        walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                                .forEach(jars::add);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        List<Path> resolved = List.copyOf(jars);
        serverJarCandidates = resolved;
        return resolved;
    }

    private static Path serverCodeSourceJar() {
        if (Bukkit.getServer() == null) {
            return null;
        }
        try {
            CodeSource source = Bukkit.getServer().getClass().getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path path = Path.of(source.getLocation().toURI());
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ClassLoader[] classLoaders() {
        List<ClassLoader> loaders = new ArrayList<>(6);
        if (Bukkit.getServer() != null) {
            ClassLoader serverLoader = Bukkit.getServer().getClass().getClassLoader();
            loaders.add(serverLoader);
            if (serverLoader != null && serverLoader.getParent() != null) {
                loaders.add(serverLoader.getParent());
            }
        }
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            loaders.add(context);
        }
        loaders.add(MinecraftLangCatalog.class.getClassLoader());
        return loaders.toArray(ClassLoader[]::new);
    }

    private static Map<String, String> parseStream(InputStream stream) {
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, String> parsed = GSON.fromJson(reader, MAP_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
