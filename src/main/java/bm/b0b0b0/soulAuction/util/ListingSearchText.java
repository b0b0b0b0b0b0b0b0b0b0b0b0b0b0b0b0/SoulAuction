package bm.b0b0b0.soulAuction.util;

import bm.b0b0b0.soulAuction.config.settings.CustomItemPluginRuleSettings;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.service.customitem.CustomItemRuleEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ListingSearchText {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final CustomItemRuleEngine CUSTOM_ITEM_RULE_ENGINE = new CustomItemRuleEngine();

    private ListingSearchText() {
    }

    public static String fromItem(String sellerName, ItemStack item, List<CustomItemPluginRuleSettings> customRules) {
        return fromItem(sellerName, item, customRules, SearchLocales.defaults());
    }

    public static String fromItem(
            String sellerName,
            ItemStack item,
            List<CustomItemPluginRuleSettings> customRules,
            Locale[] searchLocales
    ) {
        String seller = sellerName == null ? "" : sellerName.toLowerCase(Locale.ROOT);
        if (item == null || item.isEmpty()) {
            return seller;
        }
        String material = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(seller).append(' ').append(material);
        ItemMeta meta = item.getItemMeta();
        boolean customDisplayName = meta != null && meta.hasDisplayName();
        if (customDisplayName) {
            builder.append(' ').append(PLAIN.serialize(meta.displayName()).toLowerCase(Locale.ROOT));
        } else {
            appendLocalizedNames(builder, item, searchLocales, "");
        }
        builder.append(CUSTOM_ITEM_RULE_ENGINE.searchTokens(item, customRules));
        builder.append(CustomItemDisplay.extraSearchTags(item));
        return builder.toString().trim();
    }

    public static String fromItem(String sellerName, ItemStack item) {
        return fromItem(sellerName, item, List.of());
    }

    public static String resolve(AuctionListing listing) {
        return resolve(listing, SearchLocales.defaults());
    }

    public static String resolve(AuctionListing listing, Locale[] searchLocales) {
        return ListingSearchResolveCache.resolve(listing, searchLocales);
    }

    public static String buildResolveText(AuctionListing listing, Locale[] searchLocales) {
        Locale[] locales = searchLocales == null || searchLocales.length == 0 ? SearchLocales.defaults() : searchLocales;
        String stored = listing.searchText();
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        if (stored == null || stored.isBlank()) {
            return fromItem(listing.sellerName(), item, List.of(), locales);
        }
        String base = stored.toLowerCase(Locale.ROOT);
        StringBuilder enriched = new StringBuilder(base);
        appendLocalizedNames(enriched, item, locales, base);
        return enriched.toString().trim();
    }

    public static Locale[] parseSearchLocales(String raw) {
        if (raw == null || raw.isBlank()) {
            return SearchLocales.defaults();
        }
        List<Locale> locales = new ArrayList<>(4);
        for (String part : raw.split("[,;\\s]+")) {
            if (part.isBlank()) {
                continue;
            }
            locales.add(SearchLocales.parseTag(part));
        }
        if (locales.isEmpty()) {
            return SearchLocales.defaults();
        }
        return locales.toArray(Locale[]::new);
    }

    public static String localeSignature(Locale[] locales) {
        if (locales == null || locales.length == 0) {
            return "default";
        }
        StringBuilder builder = new StringBuilder(locales.length * 8);
        for (Locale locale : locales) {
            if (locale == null) {
                continue;
            }
            builder.append(SearchLocales.normalize(locale).toLanguageTag()).append(';');
        }
        return builder.toString();
    }

    private static void appendLocalizedNames(StringBuilder builder, ItemStack item, Locale[] locales, String existing) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return;
        }
        for (Locale locale : locales) {
            if (locale == null) {
                continue;
            }
            String localized = ItemTranslationCache.plain(item, locale);
            if (localized.isEmpty() || localized.equals("?") || existing.contains(localized)) {
                continue;
            }
            builder.append(' ').append(localized);
            existing = existing + ' ' + localized;
        }
    }
}
