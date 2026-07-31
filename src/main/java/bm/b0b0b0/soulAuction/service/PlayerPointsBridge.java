package bm.b0b0b0.soulAuction.service;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerPointsBridge {

    private final Object api;
    private final Method lookMethod;
    private final Method takeMethod;
    private final Method giveMethod;

    public PlayerPointsBridge(JavaPlugin plugin) {
        Object loadedApi = null;
        Method loadedLookMethod = null;
        Method loadedTakeMethod = null;
        Method loadedGiveMethod = null;
        Plugin playerPointsPlugin = plugin.getServer().getPluginManager().getPlugin("PlayerPoints");
        if (playerPointsPlugin != null && playerPointsPlugin.isEnabled()) {
            try {
                Class<?> playerPointsClass = Class.forName(
                        "org.black_ixx.playerpoints.PlayerPoints",
                        true,
                        playerPointsPlugin.getClass().getClassLoader()
                );
                Method getInstance = playerPointsClass.getMethod("getInstance");
                Object playerPoints = getInstance.invoke(null);
                if (playerPoints != null) {
                    Method getApiMethod = playerPointsClass.getMethod("getAPI");
                    loadedApi = getApiMethod.invoke(playerPoints);
                    if (loadedApi != null) {
                        Class<?> apiClass = loadedApi.getClass();
                        loadedLookMethod = apiClass.getMethod("look", UUID.class);
                        loadedTakeMethod = apiClass.getMethod("take", UUID.class, int.class);
                        loadedGiveMethod = apiClass.getMethod("give", UUID.class, int.class);
                    }
                }
            } catch (ReflectiveOperationException exception) {
                Bukkit.getLogger().warning("SoulAuction: cannot hook PlayerPoints API: " + exception.getMessage());
            }
        }
        this.api = loadedApi;
        this.lookMethod = loadedLookMethod;
        this.takeMethod = loadedTakeMethod;
        this.giveMethod = loadedGiveMethod;
    }

    public boolean available() {
        return api != null;
    }

    public boolean has(UUID playerId, int amount) {
        if (api == null) {
            return false;
        }
        try {
            Object result = lookMethod.invoke(api, playerId);
            int balance = result instanceof Number number ? number.intValue() : 0;
            return balance >= amount;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean withdraw(UUID playerId, int amount) {
        if (api == null) {
            return false;
        }
        try {
            Object result = takeMethod.invoke(api, playerId, amount);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean deposit(UUID playerId, int amount) {
        if (api == null) {
            return false;
        }
        try {
            Object result = giveMethod.invoke(api, playerId, amount);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public String format(int amount) {
        return amount + " PP";
    }
}
