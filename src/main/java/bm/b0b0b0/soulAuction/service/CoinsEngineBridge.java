package bm.b0b0b0.soulAuction.service;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoinsEngineBridge {

    private final Object api;
    private final Method getBalanceMethod;
    private final Method removeBalanceMethod;
    private final Method addBalanceMethod;
    private final String currencyId;

    public CoinsEngineBridge(JavaPlugin plugin, String currencyId) {
        this.currencyId = currencyId == null || currencyId.isBlank() ? "coins" : currencyId;
        Object loadedApi = null;
        Method look = null;
        Method take = null;
        Method give = null;
        Plugin coinsPlugin = plugin.getServer().getPluginManager().getPlugin("CoinsEngine");
        if (coinsPlugin != null) {
            try {
                Method getApi = coinsPlugin.getClass().getMethod("getAPI");
                loadedApi = getApi.invoke(coinsPlugin);
                Class<?> apiClass = loadedApi.getClass();
                look = apiClass.getMethod("getBalance", UUID.class, String.class);
                take = apiClass.getMethod("removeBalance", UUID.class, String.class, double.class);
                give = apiClass.getMethod("addBalance", UUID.class, String.class, double.class);
            } catch (Exception exception) {
                Bukkit.getLogger().warning("SoulAuction: CoinsEngine hook failed: " + exception.getMessage());
            }
        }
        this.api = loadedApi;
        this.getBalanceMethod = look;
        this.removeBalanceMethod = take;
        this.addBalanceMethod = give;
    }

    public boolean available() {
        return api != null;
    }

    public String defaultCurrencyId() {
        return currencyId;
    }

    public boolean has(UUID playerId, int amount) {
        return has(playerId, amount, currencyId);
    }

    public boolean has(UUID playerId, int amount, String currency) {
        if (api == null) {
            return false;
        }
        String resolved = currency == null || currency.isBlank() ? currencyId : currency;
        try {
            Object result = getBalanceMethod.invoke(api, playerId, resolved);
            double balance = result instanceof Number number ? number.doubleValue() : 0D;
            return balance >= amount;
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean withdraw(UUID playerId, int amount) {
        return withdraw(playerId, amount, currencyId);
    }

    public boolean withdraw(UUID playerId, int amount, String currency) {
        if (api == null) {
            return false;
        }
        String resolved = currency == null || currency.isBlank() ? currencyId : currency;
        try {
            Object result = removeBalanceMethod.invoke(api, playerId, resolved, (double) amount);
            return result instanceof Boolean value && value;
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean deposit(UUID playerId, int amount) {
        return deposit(playerId, amount, currencyId);
    }

    public boolean deposit(UUID playerId, int amount, String currency) {
        if (api == null) {
            return false;
        }
        String resolved = currency == null || currency.isBlank() ? currencyId : currency;
        try {
            Object result = addBalanceMethod.invoke(api, playerId, resolved, (double) amount);
            return result instanceof Boolean value && value;
        } catch (Exception exception) {
            return false;
        }
    }

    public String format(int amount) {
        return format(amount, currencyId);
    }

    public String format(int amount, String currency) {
        String resolved = currency == null || currency.isBlank() ? currencyId : currency;
        return amount + " " + resolved;
    }
}
