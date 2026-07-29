package bm.b0b0b0.soulAuction.util;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

public final class ListingItemEquality {

    private ListingItemEquality() {
    }

    public static boolean matches(ItemStack template, ItemStack other) {
        if (template == null || other == null || template.isEmpty() || other.isEmpty()) {
            return false;
        }
        if (template.getType() != other.getType()) {
            return false;
        }
        ItemStack a = template.clone();
        a.setAmount(1);
        ItemStack b = other.clone();
        b.setAmount(1);
        return Arrays.equals(a.serializeAsBytes(), b.serializeAsBytes());
    }
}
