package bm.b0b0b0.soulAuction.gui.admin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AdminGuiAccess {

    public static final String PERMISSION_ADMIN = "soulauction.command.admin";

    private AdminGuiAccess() {
    }

    public static boolean canOpenAdminGui(CommandSender sender) {
        if (sender.hasPermission(PERMISSION_ADMIN)) {
            return true;
        }
        return sender instanceof Player player && player.isOp();
    }
}
