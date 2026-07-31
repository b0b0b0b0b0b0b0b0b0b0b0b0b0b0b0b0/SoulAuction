package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.model.AuctionEconomyType;
import java.lang.reflect.Method;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyIntegrationProbe {

    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";
    private static final String PLAYER_POINTS_CLASS = "org.black_ixx.playerpoints.PlayerPoints";

    private EconomyIntegrationProbe() {
    }

    public static String resolvePreferredEconomy(JavaPlugin plugin) {
        if (vaultEconomyReady(plugin)) {
            return AuctionEconomyType.VAULT.name();
        }
        if (playerPointsReady(plugin)) {
            return AuctionEconomyType.PLAYER_POINTS.name();
        }
        if (coinsEngineReady(plugin)) {
            return AuctionEconomyType.COINS_ENGINE.name();
        }
        return AuctionEconomyType.VAULT.name();
    }

    public static boolean isEconomyReady(JavaPlugin plugin, String economy) {
        AuctionEconomyType type = AuctionEconomyType.fromString(economy);
        return switch (type) {
            case VAULT -> vaultEconomyReady(plugin);
            case PLAYER_POINTS -> playerPointsReady(plugin);
            case COINS_ENGINE -> coinsEngineReady(plugin);
            case EXPERIENCE -> true;
            case ITEM -> true;
        };
    }

    public static void applyPreferredEconomy(JavaPlugin plugin, AuctionDefinitionSettings definition) {
        String preferred = resolvePreferredEconomy(plugin);
        definition.economy = preferred;
        if (AuctionEconomyType.PLAYER_POINTS.name().equals(preferred)) {
            definition.currencySymbol = "";
        }
    }

    public static boolean vaultEconomyReady(JavaPlugin plugin) {
        Plugin vaultPlugin = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
            return false;
        }
        try {
            Class<?> economyClass = Class.forName(VAULT_ECONOMY_CLASS, true, vaultPlugin.getClass().getClassLoader());
            RegisteredServiceProvider<?> provider = plugin.getServer().getServicesManager().getRegistration(economyClass);
            return provider != null && provider.getProvider() != null;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static boolean playerPointsReady(JavaPlugin plugin) {
        Plugin playerPointsPlugin = plugin.getServer().getPluginManager().getPlugin("PlayerPoints");
        if (playerPointsPlugin == null || !playerPointsPlugin.isEnabled()) {
            return false;
        }
        try {
            Class<?> playerPointsClass = Class.forName(
                    PLAYER_POINTS_CLASS,
                    true,
                    playerPointsPlugin.getClass().getClassLoader()
            );
            Method getInstance = playerPointsClass.getMethod("getInstance");
            Object playerPoints = getInstance.invoke(null);
            if (playerPoints == null) {
                return false;
            }
            Method getApi = playerPointsClass.getMethod("getAPI");
            Object api = getApi.invoke(playerPoints);
            if (api == null) {
                return false;
            }
            Class<?> apiClass = api.getClass();
            apiClass.getMethod("look", java.util.UUID.class);
            apiClass.getMethod("take", java.util.UUID.class, int.class);
            apiClass.getMethod("give", java.util.UUID.class, int.class);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public static boolean coinsEngineReady(JavaPlugin plugin) {
        Plugin coinsPlugin = plugin.getServer().getPluginManager().getPlugin("CoinsEngine");
        if (coinsPlugin == null || !coinsPlugin.isEnabled()) {
            return false;
        }
        try {
            Method getApi = coinsPlugin.getClass().getMethod("getAPI");
            Object api = getApi.invoke(coinsPlugin);
            return api != null;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }
}
