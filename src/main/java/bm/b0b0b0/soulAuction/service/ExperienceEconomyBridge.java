package bm.b0b0b0.soulAuction.service;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ExperienceEconomyBridge {

    public boolean available() {
        return true;
    }

    public boolean has(UUID playerId, int levels) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        return player.getLevel() >= levels;
    }

    public boolean withdraw(UUID playerId, int levels) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || levels <= 0) {
            return false;
        }
        if (player.getLevel() < levels) {
            return false;
        }
        player.setLevel(player.getLevel() - levels);
        return true;
    }

    public boolean deposit(UUID playerId, int levels) {
        if (levels <= 0) {
            return true;
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        player.setLevel(player.getLevel() + levels);
        return true;
    }

    public String format(int levels) {
        return levels + " lvl";
    }
}
