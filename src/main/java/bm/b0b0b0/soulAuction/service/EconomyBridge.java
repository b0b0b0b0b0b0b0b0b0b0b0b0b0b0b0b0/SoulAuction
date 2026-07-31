package bm.b0b0b0.soulAuction.service;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyBridge {

    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final Object economy;
    private final Method hasMethod;
    private final Method withdrawMethod;
    private final Method depositMethod;
    private final Method formatMethod;

    public EconomyBridge(JavaPlugin plugin) {
        Object loadedEconomy = null;
        Method loadedHasMethod = null;
        Method loadedWithdrawMethod = null;
        Method loadedDepositMethod = null;
        Method loadedFormatMethod = null;
        Plugin vaultPlugin = plugin.getServer().getPluginManager().getPlugin("Vault");
        if (vaultPlugin != null && vaultPlugin.isEnabled()) {
            try {
                ClassLoader vaultLoader = vaultPlugin.getClass().getClassLoader();
                Class<?> economyClass = Class.forName(ECONOMY_CLASS, true, vaultLoader);
                RegisteredServiceProvider<?> provider = plugin.getServer().getServicesManager().getRegistration(economyClass);
                if (provider != null) {
                    loadedEconomy = provider.getProvider();
                    loadedHasMethod = economyClass.getMethod("has", OfflinePlayer.class, double.class);
                    loadedWithdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                    loadedDepositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                    loadedFormatMethod = economyClass.getMethod("format", double.class);
                }
            } catch (ReflectiveOperationException ignored) {
                loadedEconomy = null;
            }
        }
        this.economy = loadedEconomy;
        this.hasMethod = loadedHasMethod;
        this.withdrawMethod = loadedWithdrawMethod;
        this.depositMethod = loadedDepositMethod;
        this.formatMethod = loadedFormatMethod;
    }

    public boolean available() {
        return economy != null;
    }

    public boolean has(UUID playerId, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        try {
            Object result = hasMethod.invoke(economy, player, amount);
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean withdraw(UUID playerId, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        try {
            Object response = withdrawMethod.invoke(economy, player, amount);
            return transactionSuccess(response);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean deposit(UUID playerId, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        try {
            Object response = depositMethod.invoke(economy, player, amount);
            return transactionSuccess(response);
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public String format(double amount) {
        if (economy == null) {
            return String.format("%.2f", amount);
        }
        try {
            Object result = formatMethod.invoke(economy, amount);
            return result == null ? String.format("%.2f", amount) : result.toString();
        } catch (ReflectiveOperationException exception) {
            return String.format("%.2f", amount);
        }
    }

    private static boolean transactionSuccess(Object response) throws ReflectiveOperationException {
        if (response == null) {
            return false;
        }
        Method successMethod = response.getClass().getMethod("transactionSuccess");
        Object result = successMethod.invoke(response);
        return result instanceof Boolean value && value;
    }
}
