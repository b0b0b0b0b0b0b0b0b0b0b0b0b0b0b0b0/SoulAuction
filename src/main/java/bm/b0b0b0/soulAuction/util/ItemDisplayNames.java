package bm.b0b0b0.soulAuction.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemDisplayNames {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ItemDisplayNames() {
    }

    public static String plain(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return "?";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return PLAIN.serialize(meta.displayName());
        }
        return formatMaterial(item.getType().name());
    }

    private static String formatMaterial(String materialName) {
        String lower = materialName.toLowerCase().replace('_', ' ');
        if (lower.isEmpty()) {
            return materialName;
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
