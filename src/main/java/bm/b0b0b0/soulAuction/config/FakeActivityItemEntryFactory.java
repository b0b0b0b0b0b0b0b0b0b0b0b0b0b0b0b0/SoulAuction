package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemSettings;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class FakeActivityItemEntryFactory {

    private FakeActivityItemEntryFactory() {
    }

    public static FakeActivityItemSettings build(ItemStack stack, String auctionId, List<String> existingIds) {
        FakeActivityItemSettings item = new FakeActivityItemSettings();
        item.material = stack.getType().name();
        item.amount = Math.max(1, stack.getAmount());
        item.minPrice = 0;
        item.maxPrice = 0;
        item.weight = 1;
        if (auctionId != null && !auctionId.isBlank()) {
            item.auctionIds = List.of(auctionId.trim());
        }
        if (needsItemSnapshot(stack)) {
            item.itemBase64 = ItemStackCodec.encode(stack);
        }
        item.id = uniqueId(stack, existingIds);
        return item;
    }

    public static boolean matches(FakeActivityItemSettings existing, FakeActivityItemSettings candidate) {
        if (existing == null || candidate == null) {
            return false;
        }
        String existingBase64 = normalizeBase64(existing.itemBase64);
        String candidateBase64 = normalizeBase64(candidate.itemBase64);
        if (!existingBase64.isEmpty() || !candidateBase64.isEmpty()) {
            return !candidateBase64.isEmpty() && candidateBase64.equals(existingBase64);
        }
        String existingMaterial = normalizeMaterial(existing.material);
        String candidateMaterial = normalizeMaterial(candidate.material);
        return !candidateMaterial.isEmpty()
                && candidateMaterial.equals(existingMaterial)
                && existing.amount == candidate.amount;
    }

    private static boolean needsItemSnapshot(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (meta.hasDisplayName() || meta.hasLore() || meta.hasEnchants()) {
            return true;
        }
        if (meta.hasCustomModelData()) {
            return true;
        }
        return !meta.getPersistentDataContainer().isEmpty();
    }

    private static String uniqueId(ItemStack stack, List<String> existingIds) {
        List<String> taken = existingIds == null ? List.of() : existingIds;
        String base = stack.getType().name().toLowerCase(Locale.ROOT).replace('_', '-');
        if (stack.getAmount() > 1) {
            base = base + "-x" + stack.getAmount();
        }
        if (needsItemSnapshot(stack)) {
            base = base + "-custom";
        }
        if (!containsId(taken, base)) {
            return base;
        }
        for (int index = 2; index < 1000; index++) {
            String candidate = base + "-" + index;
            if (!containsId(taken, candidate)) {
                return candidate;
            }
        }
        return base + "-copy";
    }

    private static boolean containsId(List<String> ids, String id) {
        for (String existing : ids) {
            if (existing != null && existing.equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeBase64(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeMaterial(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static List<String> collectIds(List<FakeActivityItemSettings> items) {
        List<String> ids = new ArrayList<>();
        if (items == null) {
            return ids;
        }
        for (FakeActivityItemSettings item : items) {
            if (item != null && item.id != null && !item.id.isBlank()) {
                ids.add(item.id);
            }
        }
        return ids;
    }
}
