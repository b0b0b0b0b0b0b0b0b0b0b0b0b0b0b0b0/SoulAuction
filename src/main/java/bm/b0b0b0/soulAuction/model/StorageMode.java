package bm.b0b0b0.soulAuction.model;

import java.util.Locale;

public enum StorageMode {
    JSON,
    YAML,
    SQLITE,
    MYSQL;

    public static StorageMode fromString(String value) {
        if (value == null) {
            return JSON;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("FLAT_JSON_PER_ITEM")) {
            return JSON;
        }
        if (normalized.equals("FLAT_YAML_PER_ITEM")) {
            return YAML;
        }
        for (StorageMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        return JSON;
    }
}
