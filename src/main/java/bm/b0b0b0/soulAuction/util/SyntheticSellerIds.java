package bm.b0b0b0.soulAuction.util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class SyntheticSellerIds {

    private static final String PREFIX = "SoulAuction:synthetic:";

    private SyntheticSellerIds() {
    }

    public static UUID forDisplayName(String displayName) {
        String normalized = displayName.trim().toLowerCase(Locale.ROOT);
        return UUID.nameUUIDFromBytes((PREFIX + normalized).getBytes(StandardCharsets.UTF_8));
    }
}
