package bm.b0b0b0.soulAuction.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ListingItemPresentation {

    private static final TextColor GUI_ITEM_NAME = TextColor.color(0xF5D0FE);

    private ListingItemPresentation() {
    }

    public static void applyAuctionGuiName(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        item.editMeta(meta -> meta.displayName(styledGuiName(item, meta)));
    }

    private static Component styledGuiName(ItemStack item, ItemMeta meta) {
        Component base = resolveVisibleName(item, meta);
        return base.decoration(TextDecoration.ITALIC, false).colorIfAbsent(GUI_ITEM_NAME);
    }

    private static Component resolveVisibleName(ItemStack item, ItemMeta meta) {
        if (meta.hasDisplayName()) {
            return meta.displayName();
        }
        if (meta.hasItemName()) {
            return meta.itemName();
        }
        return Component.translatable(item.translationKey());
    }
}
