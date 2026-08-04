package bm.b0b0b0.soulAuction.util;

import org.bukkit.entity.Player;

public final class PermissionChecks {

    private PermissionChecks() {
    }

    public static boolean has(Player player, String permission) {
        return permission == null || permission.isBlank() || player.hasPermission(permission);
    }
}
