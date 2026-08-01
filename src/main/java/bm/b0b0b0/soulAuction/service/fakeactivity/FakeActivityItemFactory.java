package bm.b0b0b0.soulAuction.service.fakeactivity;

import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemSettings;
import bm.b0b0b0.soulAuction.util.ItemStackCodec;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class FakeActivityItemFactory {

    private FakeActivityItemFactory() {
    }

    public static ItemStack build(FakeActivityItemSettings item) {
        if (item == null) {
            return null;
        }
        if (item.itemBase64 != null && !item.itemBase64.isBlank()) {
            ItemStack decoded = ItemStackCodec.decode(item.itemBase64);
            if (!decoded.isEmpty()) {
                return decoded.clone();
            }
        }
        if (item.material == null || item.material.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(item.material.trim());
        if (material == null || material.isAir()) {
            return null;
        }
        int amount = Math.max(1, item.amount);
        amount = Math.min(amount, material.getMaxStackSize());
        return new ItemStack(material, amount);
    }
}
