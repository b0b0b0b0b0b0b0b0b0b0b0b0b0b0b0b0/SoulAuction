package bm.b0b0b0.soulAuction.util;

import java.util.Locale;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Best-effort labels for ItemsAdder / Oraxen / MMOItems via persistent data and display name.
 */
public final class CustomItemDisplay {

    private CustomItemDisplay() {
    }

    public static String extraSearchTags(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            builder.append(' ').append(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(meta.displayName()).toLowerCase(Locale.ROOT));
        }
        if (meta != null && meta.hasCustomModelData()) {
            builder.append(" cmd:").append(meta.getCustomModelData());
        }
        appendNamespaceKey(builder, item, "itemsadder");
        appendNamespaceKey(builder, item, "oraxen");
        appendNamespaceKey(builder, item, "mmoitems");
        return builder.toString();
    }

    private static void appendNamespaceKey(StringBuilder builder, ItemStack item, String namespace) {
        org.bukkit.persistence.PersistentDataContainer container = item.getItemMeta() == null
                ? null
                : item.getItemMeta().getPersistentDataContainer();
        if (container == null) {
            return;
        }
        for (org.bukkit.NamespacedKey key : container.getKeys()) {
            if (!key.getNamespace().equalsIgnoreCase(namespace)) {
                continue;
            }
            builder.append(' ').append(key.getKey().toLowerCase(Locale.ROOT));
            String value = container.get(key, org.bukkit.persistence.PersistentDataType.STRING);
            if (value != null) {
                builder.append(':').append(value.toLowerCase(Locale.ROOT));
            }
        }
    }
}
