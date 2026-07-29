package bm.b0b0b0.soulAuction.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.PlaceholderAPI;

public final class PlaceholderApiBridge {

    private static Boolean available;

    private PlaceholderApiBridge() {
    }

    public static boolean available() {
        if (available == null) {
            available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        }
        return available;
    }

    public static String apply(Player player, String text) {
        if (text == null || text.isEmpty() || !text.contains("%")) {
            return text;
        }
        if (!available() || player == null) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}
