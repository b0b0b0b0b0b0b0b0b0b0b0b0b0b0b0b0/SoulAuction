package bm.b0b0b0.soulAuction.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import java.util.function.Supplier;

public final class MessageService {

    public static final String DEFAULT_LOCALE = "en";

    private static final List<String> JAR_LOCALES = List.of("en", "ru");

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private final Map<String, FileConfiguration> bundles = new LinkedHashMap<>();
    private Supplier<Boolean> respectDisabledMessages = () -> true;
    private Supplier<String> forcedLocaleId = () -> null;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    public void reload() {
        bundles.clear();
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) {
            langDir.mkdirs();
        }
        for (String localeId : JAR_LOCALES) {
            ensureJarLocaleFile(langDir, localeId);
        }
        loadDiscoveredLocaleFiles(langDir);
        File legacy = new File(plugin.getDataFolder(), "messages.yml");
        if (legacy.exists() && bundles.get("ru") != null) {
            FileConfiguration legacyConfig = YamlConfiguration.loadConfiguration(legacy);
            mergeMissingKeys(bundles.get("ru"), legacyConfig);
        }
    }

    private void ensureJarLocaleFile(File langDir, String localeId) {
        File target = new File(langDir, "messages_" + localeId + ".yml");
        if (!target.exists()) {
            plugin.saveResource("lang/messages_" + localeId + ".yml", false);
        }
    }

    private void loadDiscoveredLocaleFiles(File langDir) {
        File[] files = langDir.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        FileConfiguration englishBundled = loadBundledDefaults("en");
        for (File file : files) {
            String localeId = localeIdFromFileName(file.getName());
            if (localeId == null) {
                continue;
            }
            FileConfiguration disk = YamlConfiguration.loadConfiguration(file);
            FileConfiguration bundled = loadBundledDefaults(localeId);
            if (bundled != null) {
                mergeMissingKeys(disk, bundled);
            } else if (englishBundled != null) {
                mergeMissingKeys(disk, englishBundled);
            }
            bundles.put(localeId, disk);
        }
    }

    private static String localeIdFromFileName(String fileName) {
        if (!fileName.startsWith("messages_") || !fileName.endsWith(".yml")) {
            return null;
        }
        String localeId = fileName.substring("messages_".length(), fileName.length() - ".yml".length()).trim();
        if (localeId.isEmpty()) {
            return null;
        }
        return localeId.toLowerCase(Locale.ROOT);
    }

    public List<String> loadedLocaleIds() {
        return List.copyOf(bundles.keySet());
    }

    public void setRespectDisabledMessages(Supplier<Boolean> respectDisabledMessages) {
        if (respectDisabledMessages != null) {
            this.respectDisabledMessages = respectDisabledMessages;
        }
    }

    public void setForcedLocaleSupplier(Supplier<String> forcedLocaleId) {
        this.forcedLocaleId = forcedLocaleId != null ? forcedLocaleId : () -> null;
    }

    public String resolveLocale(Player player) {
        String configured = configuredLocaleId();
        if (configured != null) {
            return configured;
        }
        if (player == null) {
            return DEFAULT_LOCALE;
        }
        return normalizeLocaleId(player.locale());
    }

    public String resolveLocale(UUID viewerId) {
        String configured = configuredLocaleId();
        if (configured != null) {
            return configured;
        }
        if (viewerId == null) {
            return DEFAULT_LOCALE;
        }
        Player player = Bukkit.getPlayer(viewerId);
        return resolveLocale(player);
    }

    public String resolveLocale(CommandSender sender) {
        if (sender instanceof Player player) {
            return resolveLocale(player);
        }
        String configured = configuredLocaleId();
        return configured != null ? configured : DEFAULT_LOCALE;
    }

    public Component component(String key) {
        return component(DEFAULT_LOCALE, key, Collections.emptyMap());
    }

    public Component component(String key, Map<String, String> placeholders) {
        return component(DEFAULT_LOCALE, key, placeholders);
    }

    public Component component(Player player, String key) {
        return component(resolveLocale(player), key, Collections.emptyMap());
    }

    public Component component(Player player, String key, Map<String, String> placeholders) {
        return component(resolveLocale(player), key, placeholders);
    }

    public Component component(UUID viewerId, String key) {
        return component(resolveLocale(viewerId), key, Collections.emptyMap());
    }

    public Component component(UUID viewerId, String key, Map<String, String> placeholders) {
        return component(resolveLocale(viewerId), key, placeholders);
    }

    public Component component(String localeId, String key, Map<String, String> placeholders) {
        if (isDisabled(localeId, key)) {
            return Component.empty();
        }
        String value = template(localeId, key);
        value = value.replace("{prefix}", template(localeId, "prefix"));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return deserializeFormatted(value);
    }

    public List<Component> components(String key) {
        return components(DEFAULT_LOCALE, key, Collections.emptyMap());
    }

    public List<Component> components(String key, Map<String, String> placeholders) {
        return components(DEFAULT_LOCALE, key, placeholders);
    }

    public List<Component> components(UUID viewerId, String key) {
        return components(resolveLocale(viewerId), key, Collections.emptyMap());
    }

    public List<Component> components(UUID viewerId, String key, Map<String, String> placeholders) {
        return components(resolveLocale(viewerId), key, placeholders);
    }

    public List<Component> components(String localeId, String key, Map<String, String> placeholders) {
        return components(localeId, key, placeholders, Map.of());
    }

    public List<Component> components(
            String localeId,
            String key,
            Map<String, String> placeholders,
            Map<String, Component> componentPlaceholders
    ) {
        List<String> lines = stringList(localeId, key);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<Component> output = new ArrayList<>(lines.size());
        for (String line : lines) {
            output.add(deserializeLine(localeId, line, placeholders, componentPlaceholders));
        }
        return output;
    }

    private Component deserializeLine(
            String localeId,
            String line,
            Map<String, String> placeholders,
            Map<String, Component> componentPlaceholders
    ) {
        String formatted = line.replace("{prefix}", template(localeId, "prefix"));
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        if (componentPlaceholders == null || componentPlaceholders.isEmpty()) {
            return deserializeFormatted(formatted);
        }
        TagResolver.Builder resolver = TagResolver.builder();
        for (Map.Entry<String, Component> entry : componentPlaceholders.entrySet()) {
            resolver.resolver(Placeholder.component(entry.getKey(), entry.getValue()));
        }
        return deserializeFormatted(formatted, resolver.build());
    }

    private Component deserializeFormatted(String formatted) {
        return MessageComponents.withoutDefaultItalic(miniMessage.deserialize(formatted));
    }

    private Component deserializeFormatted(String formatted, TagResolver resolver) {
        return MessageComponents.withoutDefaultItalic(miniMessage.deserialize(formatted, resolver));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Collections.emptyMap());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        if (!(sender instanceof Audience audience)) {
            return;
        }
        String localeId = resolveLocale(sender);
        sendLocalized(audience, localeId, key, placeholders);
    }

    public void send(Player player, String key) {
        send(player, key, Collections.emptyMap());
    }

    public void send(Player player, String key, Map<String, String> placeholders) {
        send(player, key, placeholders, Map.of());
    }

    public void send(Player player, String key, Map<String, String> placeholders, Map<String, Component> componentPlaceholders) {
        sendLocalized(player, resolveLocale(player), key, placeholders, componentPlaceholders);
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        if (isDisabled(DEFAULT_LOCALE, key)) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, key, placeholders);
        }
    }

    public List<Component> componentsFromTemplates(
            UUID viewerId,
            List<String> templates,
            Map<String, String> placeholders
    ) {
        return componentsFromTemplates(resolveLocale(viewerId), templates, placeholders);
    }

    public List<Component> componentsFromTemplates(
            String localeId,
            List<String> templates,
            Map<String, String> placeholders
    ) {
        if (templates == null || templates.isEmpty()) {
            return List.of();
        }
        List<Component> output = new ArrayList<>(templates.size());
        for (String line : templates) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String formatted = line.replace("{prefix}", template(localeId, "prefix"));
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            output.add(deserializeFormatted(formatted));
        }
        return output;
    }

    public String raw(String key) {
        return raw(DEFAULT_LOCALE, key);
    }

    public String raw(UUID viewerId, String key) {
        return template(resolveLocale(viewerId), key);
    }

    public String raw(String localeId, String key) {
        return template(localeId, key);
    }

    public java.util.Locale javaLocale(UUID viewerId) {
        return java.util.Locale.forLanguageTag(resolveLocale(viewerId));
    }

    private void sendLocalized(Audience audience, String localeId, String key, Map<String, String> placeholders) {
        sendLocalized(audience, localeId, key, placeholders, Map.of());
    }

    private void sendLocalized(
            Audience audience,
            String localeId,
            String key,
            Map<String, String> placeholders,
            Map<String, Component> componentPlaceholders
    ) {
        if (isDisabled(localeId, key)) {
            return;
        }
        List<Component> lines = components(localeId, key, placeholders, componentPlaceholders);
        if (!lines.isEmpty()) {
            for (Component line : lines) {
                audience.sendMessage(line);
            }
            return;
        }
        audience.sendMessage(component(localeId, key, placeholders));
    }

    private String configuredLocaleId() {
        String raw = forcedLocaleId.get();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return normalizeBundledLocaleId(raw);
    }

    private String normalizeBundledLocaleId(String raw) {
        String language = raw.toLowerCase(Locale.ROOT).trim();
        if (bundles.containsKey(language)) {
            return language;
        }
        if (language.startsWith("ru") && bundles.containsKey("ru")) {
            return "ru";
        }
        if (language.startsWith("en") && bundles.containsKey("en")) {
            return "en";
        }
        int separator = language.indexOf('-');
        if (separator > 0) {
            String base = language.substring(0, separator);
            if (bundles.containsKey(base)) {
                return base;
            }
        }
        return DEFAULT_LOCALE;
    }

    private String normalizeLocaleId(Locale locale) {
        if (locale == null) {
            return DEFAULT_LOCALE;
        }
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        if (!language.isBlank() && bundles.containsKey(language)) {
            return language;
        }
        String tag = locale.toLanguageTag().toLowerCase(Locale.ROOT);
        if (bundles.containsKey(tag)) {
            return tag;
        }
        int separator = tag.indexOf('-');
        if (separator > 0) {
            String base = tag.substring(0, separator);
            if (bundles.containsKey(base)) {
                return base;
            }
        }
        if (tag.startsWith("ru") && bundles.containsKey("ru")) {
            return "ru";
        }
        return DEFAULT_LOCALE;
    }

    private FileConfiguration loadBundledDefaults(String localeId) {
        String resourcePath = "lang/messages_" + localeId + ".yml";
        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) {
            return null;
        }
        try (InputStream input = stream) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return null;
        }
    }

    private FileConfiguration bundle(String localeId) {
        FileConfiguration config = bundles.get(localeId);
        if (config != null) {
            return config;
        }
        FileConfiguration fallback = bundles.get(DEFAULT_LOCALE);
        return fallback == null ? new YamlConfiguration() : fallback;
    }

    private String template(String localeId, String key) {
        String value = bundle(localeId).getString(key);
        if (value != null) {
            return value;
        }
        if (!DEFAULT_LOCALE.equals(localeId)) {
            value = bundle(DEFAULT_LOCALE).getString(key);
            if (value != null) {
                return value;
            }
        }
        return key;
    }

    private List<String> stringList(String localeId, String key) {
        List<String> lines = bundle(localeId).getStringList(key);
        if (lines != null && !lines.isEmpty()) {
            return lines;
        }
        if (!DEFAULT_LOCALE.equals(localeId)) {
            lines = bundle(DEFAULT_LOCALE).getStringList(key);
            if (lines != null && !lines.isEmpty()) {
                return lines;
            }
        }
        return List.of();
    }

    private boolean isDisabled(String localeId, String key) {
        if (!Boolean.TRUE.equals(respectDisabledMessages.get())) {
            return false;
        }
        return bundle(localeId).getStringList("disabled-messages").stream()
                .anyMatch(entry -> entry.equalsIgnoreCase(key));
    }

    private static void mergeMissingKeys(FileConfiguration target, FileConfiguration overlay) {
        for (String key : overlay.getKeys(true)) {
            if (overlay.isConfigurationSection(key)) {
                continue;
            }
            if (!target.contains(key)) {
                target.set(key, overlay.get(key));
            }
        }
    }
}
