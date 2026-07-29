package bm.b0b0b0.soulAuction.util;

import java.util.Base64;
import org.bukkit.inventory.ItemStack;

public final class ItemStackCodec {

    private ItemStackCodec() {
    }

    public static String encode(ItemStack itemStack) {
        return Base64.getEncoder().encodeToString(itemStack.serializeAsBytes());
    }

    public static ItemStack decode(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ItemStack.deserializeBytes(bytes);
    }
}
