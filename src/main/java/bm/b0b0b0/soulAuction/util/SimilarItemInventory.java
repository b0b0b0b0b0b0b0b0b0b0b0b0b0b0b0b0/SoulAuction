package bm.b0b0b0.soulAuction.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class SimilarItemInventory {

    private SimilarItemInventory() {
    }

    public static int countSimilar(PlayerInventory inventory, ItemStack template) {
        if (inventory == null || template == null || template.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (ListingItemEquality.matches(template, stack)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    public static int removeSimilar(PlayerInventory inventory, ItemStack template, int amount) {
        if (inventory == null || template == null || template.isEmpty() || amount <= 0) {
            return 0;
        }
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.isEmpty() || !ListingItemEquality.matches(template, stack)) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (take >= stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                ItemStack updated = stack.clone();
                updated.setAmount(stack.getAmount() - take);
                inventory.setItem(slot, updated);
            }
        }
        return amount - remaining;
    }
}
