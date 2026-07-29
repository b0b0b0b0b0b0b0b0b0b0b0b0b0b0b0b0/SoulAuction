package bm.b0b0b0.soulAuction.model;

import java.util.Locale;

public enum TaxMode {
    FLAT,
    VAT,
    CAPITALISM;

    public static TaxMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return FLAT;
        }
        try {
            return TaxMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return FLAT;
        }
    }
}
