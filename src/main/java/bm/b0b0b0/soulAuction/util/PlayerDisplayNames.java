package bm.b0b0b0.soulAuction.util;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class PlayerDisplayNames {

    private PlayerDisplayNames() {
    }

    public static String resolve(UUID uuid, String storedName) {
        if (storedName != null && !storedName.isBlank()) {
            return storedName;
        }
        if (uuid == null) {
            return "-";
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        return uuid.toString();
    }
}
