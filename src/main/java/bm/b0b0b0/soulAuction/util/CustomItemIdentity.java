package bm.b0b0b0.soulAuction.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public final class CustomItemIdentity {

    private static final List<String> KNOWN_NAMESPACES = List.of(
            "itemsadder", "oraxen", "mmoitems", "nexo", "executableitems", "ecoitems",
            "mythiccrucible", "slimefun", "headdatabase", "nova", "denizen", "advanceditems",
            "customcrafting", "zhead", "magiccosmetics", "hmccosmetics", "zitems", "craftengine",
            "executableblocks"
    );

    private CustomItemIdentity() {
    }

    public record PluginItemRef(String namespace, String key) {
    }

    public static List<PluginItemRef> detect(ItemStack item) {
        List<PluginItemRef> refs = new ArrayList<>();
        if (item == null || item.isEmpty()) {
            return refs;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return refs;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (NamespacedKey namespacedKey : container.getKeys()) {
            String namespace = namespacedKey.getNamespace().toLowerCase(Locale.ROOT);
            if (KNOWN_NAMESPACES.contains(namespace) || namespace.contains("item")) {
                refs.add(new PluginItemRef(namespace, namespacedKey.getKey().toLowerCase(Locale.ROOT)));
            }
        }
        return refs;
    }

    public static String primaryLabel(ItemStack item) {
        List<PluginItemRef> refs = detect(item);
        if (refs.isEmpty()) {
            return "";
        }
        PluginItemRef first = refs.get(0);
        return first.namespace() + ":" + first.key();
    }
}
