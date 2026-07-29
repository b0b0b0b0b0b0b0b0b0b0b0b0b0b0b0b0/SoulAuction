package bm.b0b0b0.soulAuction.service;

import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

public final class PermissionPriorityResolver {

    private final String pluginPrefix = "soulauction.priority.";

    public PermissionPriorityResolver() {
    }

    public int resolve(Player player) {
        int max = 0;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            if (!attachment.getValue()) {
                continue;
            }
            String permission = attachment.getPermission();
            if (!permission.toLowerCase(Locale.ROOT).startsWith(pluginPrefix)) {
                continue;
            }
            String suffix = permission.substring(pluginPrefix.length());
            try {
                max = Math.max(max, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
                // ignore malformed nodes
            }
        }
        return max;
    }

    public int resolve(java.util.UUID sellerId) {
        org.bukkit.entity.Player online = org.bukkit.Bukkit.getPlayer(sellerId);
        if (online == null) {
            return 0;
        }
        return resolve(online);
    }
}
