package bm.b0b0b0.soulAuction.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class CommandSuggestions {

    private CommandSuggestions() {
    }

    public static List<String> filter(String prefix, Collection<String> candidates) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(candidate);
            }
        }
        matches.sort(Comparator.naturalOrder());
        return matches;
    }

    public static List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
