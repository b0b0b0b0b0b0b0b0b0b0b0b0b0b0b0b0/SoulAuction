package bm.b0b0b0.soulAuction.service;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class EconomyBridge {

    private final Economy economy;

    public EconomyBridge(JavaPlugin plugin) {
        this.economy = resolveEconomy(plugin);
    }

    public boolean available() {
        return economy != null;
    }

    public boolean has(UUID playerId, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return economy.has(player, amount);
    }

    public boolean withdraw(UUID playerId, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(UUID playerId, double amount) {
        if (economy == null) {
            return false;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    public String format(double amount) {
        if (economy == null) {
            return String.format("%.2f", amount);
        }
        return economy.format(amount);
    }

    private Economy resolveEconomy(JavaPlugin plugin) {
        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return null;
        }
        return provider.getProvider();
    }
}
