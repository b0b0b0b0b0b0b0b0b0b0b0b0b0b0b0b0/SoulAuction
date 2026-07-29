package bm.b0b0b0.soulAuction.service;

import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class PermissionLimitResolver {

    private final String pluginPermissionPrefix;

    public PermissionLimitResolver(String pluginName) {
        this.pluginPermissionPrefix = pluginName.toLowerCase(Locale.ROOT);
    }

    public int resolveAuctionLimit(Player player, String auctionId, int defaultLimit) {
        String normalizedAuctionId = auctionId.toLowerCase(Locale.ROOT);
        return resolveLimit(player.getEffectivePermissions(), normalizedAuctionId, defaultLimit);
    }

    public int resolveGlobalLimit(Player player, int defaultLimit) {
        return resolveLimit(player.getEffectivePermissions(), "all", defaultLimit);
    }

    private int resolveLimit(Set<PermissionAttachmentInfo> permissions, String scope, int fallbackLimit) {
        int bestLimit = fallbackLimit;
        String prefix = pluginPermissionPrefix + "." + scope + ".";
        for (PermissionAttachmentInfo info : permissions) {
            if (!info.getValue()) {
                continue;
            }
            String permission = info.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }
            String limitPart = permission.substring(prefix.length());
            int parsedLimit;
            try {
                parsedLimit = Integer.parseInt(limitPart);
            } catch (NumberFormatException exception) {
                continue;
            }
            if (parsedLimit > bestLimit) {
                bestLimit = parsedLimit;
            }
        }
        return Math.max(0, bestLimit);
    }
}
