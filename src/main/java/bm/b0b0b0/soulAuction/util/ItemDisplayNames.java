package bm.b0b0b0.soulAuction.util;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemDisplayNames {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final TextColor CHAT_ITEM = TextColor.color(0xF5D0FE);

    private ItemDisplayNames() {
    }

    public static Component chatItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return Component.text("?", CHAT_ITEM);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return meta.displayName().decoration(TextDecoration.ITALIC, false).colorIfAbsent(CHAT_ITEM);
            }
            if (meta.hasItemName()) {
                return meta.itemName().decoration(TextDecoration.ITALIC, false).colorIfAbsent(CHAT_ITEM);
            }
        }
        return Component.translatable(item.translationKey())
                .decoration(TextDecoration.ITALIC, false)
                .color(CHAT_ITEM);
    }

    public static String plain(ItemStack item) {
        return plain(item, Locale.ENGLISH);
    }

    public static String plain(ItemStack item, Locale locale) {
        if (item == null || item.isEmpty()) {
            return "?";
        }
        Locale effective = locale == null ? Locale.ENGLISH : locale;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                return serializeForLocale(meta.displayName(), effective);
            }
            if (meta.hasItemName()) {
                return serializeForLocale(meta.itemName(), effective);
            }
        }
        Component name = Component.translatable(item.translationKey());
        return serializeForLocale(name, effective);
    }

    private static String serializeForLocale(Component component, Locale locale) {
        return PLAIN.serialize(GlobalTranslator.render(component, locale));
    }
}
