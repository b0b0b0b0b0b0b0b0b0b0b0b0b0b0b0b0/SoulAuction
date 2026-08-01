package bm.b0b0b0.soulAuction.service;

import java.util.Locale;

public enum SellerSkinSource {
    AUTO,
    SKINSRESTORER,
    MOJANG,
    OFF;

    public static SellerSkinSource parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (normalized) {
            case "auto" -> AUTO;
            case "skins-restorer", "skinsrestorer", "sr" -> SKINSRESTORER;
            case "mojang", "session", "session-server" -> MOJANG;
            case "off", "false", "none", "disabled" -> OFF;
            default -> AUTO;
        };
    }
}
