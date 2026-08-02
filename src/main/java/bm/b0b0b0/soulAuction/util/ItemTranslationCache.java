package bm.b0b0b0.soulAuction.util;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemTranslationCache {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private ItemTranslationCache() {
    }

    public static String plain(ItemStack item, Locale locale) {
        if (item == null || item.isEmpty()) {
            return "?";
        }
        Locale effective = SearchLocales.normalize(locale);
        String cacheKey = cacheKey(item, effective);
        return CACHE.computeIfAbsent(cacheKey, ignored -> translatePlain(item, effective));
    }

    public static void clear() {
        CACHE.clear();
    }

    private static String translatePlain(ItemStack item, Locale locale) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return plainComponent(meta.displayName(), locale);
            }
            if (meta.hasItemName()) {
                return plainComponent(meta.itemName(), locale);
            }
        }
        String fromCatalog = MinecraftLangCatalog.translate(translationKey(item), locale);
        if (fromCatalog != null && !fromCatalog.isBlank()) {
            return fromCatalog.toLowerCase(Locale.ROOT).trim();
        }
        String translated = ItemDisplayNames.plain(item, locale);
        if (translated == null || translated.isBlank()) {
            return "?";
        }
        return translated.toLowerCase(Locale.ROOT).trim();
    }

    private static String plainComponent(net.kyori.adventure.text.Component component, Locale locale) {
        if (component == null) {
            return "?";
        }
        String translated = ItemDisplayNames.plain(component, locale);
        if (translated == null || translated.isBlank()) {
            return "?";
        }
        return translated.toLowerCase(Locale.ROOT).trim();
    }

    private static String translationKey(ItemStack item) {
        String key = item.translationKey();
        if (key != null && !key.isBlank()) {
            return key;
        }
        return "item.minecraft." + item.getType().name().toLowerCase(Locale.ROOT);
    }

    private static String cacheKey(ItemStack item, Locale locale) {
        StringBuilder builder = new StringBuilder(96);
        builder.append(locale.toLanguageTag()).append('|');
        builder.append(item.getType().name()).append('|');
        builder.append(translationKey(item));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                builder.append("|display:").append(PLAIN.serialize(meta.displayName()));
            } else if (meta.hasItemName()) {
                builder.append("|item-name:").append(PLAIN.serialize(meta.itemName()));
            }
        }
        return builder.toString();
    }
}
