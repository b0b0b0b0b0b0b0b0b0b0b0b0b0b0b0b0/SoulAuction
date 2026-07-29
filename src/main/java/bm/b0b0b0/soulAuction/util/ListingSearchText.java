package bm.b0b0b0.soulAuction.util;

import bm.b0b0b0.soulAuction.model.AuctionListing;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ListingSearchText {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ListingSearchText() {
    }

    public static String fromItem(String sellerName, ItemStack item) {
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
        return builder.toString().trim();
    }

    public static String resolve(AuctionListing listing) {
        if (listing.searchText() != null && !listing.searchText().isBlank()) {
            return listing.searchText().toLowerCase(Locale.ROOT);
        }
        ItemStack item = ItemStackCodec.decode(listing.itemBase64());
        return fromItem(listing.sellerName(), item);
    }
}
