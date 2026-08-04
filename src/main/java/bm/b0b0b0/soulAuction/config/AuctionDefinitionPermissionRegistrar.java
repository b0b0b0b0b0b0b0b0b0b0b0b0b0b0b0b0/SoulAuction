package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import java.util.List;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionDefinitionPermissionRegistrar {

    private AuctionDefinitionPermissionRegistrar() {
    }

    public static void register(JavaPlugin plugin, List<AuctionDefinitionSettings> definitions) {
        if (definitions == null) {
            return;
        }
        for (AuctionDefinitionSettings definition : definitions) {
            if (definition == null || definition.id == null || definition.id.isBlank()) {
                continue;
            }
            registerIfAbsent(plugin, definition.openPermission, "Open auction " + definition.id);
            registerIfAbsent(plugin, definition.buyPermission, "Buy in auction " + definition.id);
            registerIfAbsent(plugin, definition.sellPermission, "Sell in auction " + definition.id);
        }
    }

    private static void registerIfAbsent(JavaPlugin plugin, String name, String description) {
        if (name == null || name.isBlank()) {
            return;
        }
        if (plugin.getServer().getPluginManager().getPermission(name) != null) {
            return;
        }
        plugin.getServer().getPluginManager().addPermission(new Permission(name, description, PermissionDefault.TRUE));
    }
}
