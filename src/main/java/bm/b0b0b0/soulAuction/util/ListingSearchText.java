package bm.b0b0b0.soulAuction.util;

import bm.b0b0b0.soulAuction.config.settings.CustomItemPluginRuleSettings;
import bm.b0b0b0.soulAuction.model.AuctionListing;
import bm.b0b0b0.soulAuction.service.customitem.CustomItemRuleEngine;
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
        String seller = sellerName == null ? "" : sellerName.toLowerCase(Locale.ROOT);
        if (item == null || item.isEmpty()) {
            return seller;
        }
        String material = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder builder = new StringBuilder(seller).append(' ').append(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            builder.append(' ').append(PLAIN.serialize(meta.displayName()).toLowerCase(Locale.ROOT));
        }
        builder.append(CUSTOM_ITEM_RULE_ENGINE.searchTokens(item, customRules));
        builder.append(CustomItemDisplay.extraSearchTags(item));
        return builder.toString().trim();
    }

    public static String fromItem(String sellerName, ItemStack item) {
        return fromItem(sellerName, item, List.of());
    }

    public static String resolve(AuctionListing listing) {
        if (listing.searchText() != null && !listing.searchText().isBlank()) {
            return listing.searchText().toLowerCase(Locale.ROOT);
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        return fromItem(listing.sellerName(), item);
    }
}
