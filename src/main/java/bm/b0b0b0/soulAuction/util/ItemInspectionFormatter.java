package bm.b0b0b0.soulAuction.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemInspectionFormatter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ItemInspectionFormatter() {
    }

    public static List<String> formatTags(ItemStack item) {
        List<String> lines = new ArrayList<>();
        if (item == null || item.isEmpty()) {
            lines.add("empty hand");
            return lines;
        }
        lines.add("material=" + item.getType().name() + " x" + item.getAmount());
        for (CustomItemIdentity.PluginItemRef ref : CustomItemIdentity.detect(item)) {
            lines.add("ref " + ref.namespace() + ":" + ref.key());
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return lines;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        for (org.bukkit.NamespacedKey key : container.getKeys()) {
            lines.add(key.getNamespace() + ":" + key.getKey() + " = " + readValue(container, key));
        }
        return lines;
    }

    public static List<String> formatNbt(ItemStack item) {
        List<String> lines = new ArrayList<>();
        if (item == null || item.isEmpty()) {
            lines.add("empty hand");
            return lines;
        }
        Map<String, Object> serialized = item.serialize();
        String json = GSON.toJson(serialized);
        for (String chunk : chunk(json, 320)) {
            lines.add(chunk);
        }
        return lines;
    }

    private static String readValue(PersistentDataContainer container, org.bukkit.NamespacedKey key) {
        if (container.has(key, PersistentDataType.STRING)) {
            return container.get(key, PersistentDataType.STRING);
        }
        if (container.has(key, PersistentDataType.INTEGER)) {
            return String.valueOf(container.get(key, PersistentDataType.INTEGER));
        }
        if (container.has(key, PersistentDataType.LONG)) {
            return String.valueOf(container.get(key, PersistentDataType.LONG));
        }
        if (container.has(key, PersistentDataType.DOUBLE)) {
            return String.valueOf(container.get(key, PersistentDataType.DOUBLE));
        }
        if (container.has(key, PersistentDataType.FLOAT)) {
            return String.valueOf(container.get(key, PersistentDataType.FLOAT));
        }
        if (container.has(key, PersistentDataType.BYTE)) {
            return String.valueOf(container.get(key, PersistentDataType.BYTE));
        }
        if (container.has(key, PersistentDataType.SHORT)) {
            return String.valueOf(container.get(key, PersistentDataType.SHORT));
        }
        if (container.has(key, PersistentDataType.TAG_CONTAINER)) {
            return "{compound}";
        }
        if (container.has(key, PersistentDataType.BYTE_ARRAY)) {
            byte[] bytes = container.get(key, PersistentDataType.BYTE_ARRAY);
            return "bytes[" + (bytes == null ? 0 : bytes.length) + "]";
        }
        return "?";
    }

    private static List<String> chunk(String text, int maxLen) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            parts.add("");
            return parts;
        }
        int index = 0;
        while (index < text.length()) {
            int end = Math.min(text.length(), index + maxLen);
            parts.add(text.substring(index, end));
            index = end;
        }
        return parts;
    }
}
