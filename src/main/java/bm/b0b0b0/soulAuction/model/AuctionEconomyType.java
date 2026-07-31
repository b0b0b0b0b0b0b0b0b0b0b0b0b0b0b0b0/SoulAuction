package bm.b0b0b0.soulAuction.model;

import java.util.Locale;

public enum AuctionEconomyType {
    VAULT,
    PLAYER_POINTS,
    EXPERIENCE,
    COINS_ENGINE,
    ITEM;

    public String messageKey() {
        return "currency-" + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static AuctionEconomyType fromString(String value) {
        if (value == null) {
            return VAULT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (AuctionEconomyType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return VAULT;
    }
}
