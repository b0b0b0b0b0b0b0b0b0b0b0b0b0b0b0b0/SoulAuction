package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class PriceLimitResolver {

    private static final String MIN_PREFIX = "soulauction.price.min.";
    private static final String MAX_PREFIX = "soulauction.price.max.";

    public record PriceBounds(int minPrice, int maxPrice) {
        public boolean isValid(int price) {
            return price >= minPrice && price <= maxPrice;
        }
    }

    public PriceBounds resolve(Player player, AuctionDefinitionSettings definition, AuctionSettings.LimitsSettings limits) {
        int min = definition.minPrice > 0 ? definition.minPrice : limits.minPrice;
        int max = definition.maxPrice > 0 ? definition.maxPrice : limits.maxPrice;
        if (player != null) {
            min = Math.max(min, resolvePermissionFloor(player, MIN_PREFIX));
            max = Math.max(max, resolvePermissionCeiling(player, MAX_PREFIX, max));
        }
        if (min > max) {
            min = max;
        }
        return new PriceBounds(Math.max(1, min), Math.max(1, max));
    }

    private int resolvePermissionFloor(Player player, String prefix) {
        int best = 0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }
            int parsed = parseSuffix(permission.substring(prefix.length()));
            if (parsed > best) {
                best = parsed;
            }
        }
        return best;
    }

    private int resolvePermissionCeiling(Player player, String prefix, int currentMax) {
        int best = currentMax;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }
            int parsed = parseSuffix(permission.substring(prefix.length()));
            if (parsed > best) {
                best = parsed;
            }
        }
        return best;
    }

    private int parseSuffix(String suffix) {
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
