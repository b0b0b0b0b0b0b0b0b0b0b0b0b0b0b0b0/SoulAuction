package bm.b0b0b0.soulAuction.service.economy;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ItemCurrencyService {

    public boolean available(AuctionDefinitionSettings definition) {
        return resolveMaterial(definition) != null;
    }

    public boolean has(UUID playerId, int units, AuctionDefinitionSettings definition) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return false;
        }
        Material material = resolveMaterial(definition);
        if (material == null) {
            return false;
        }
        return countMaterial(player, material) >= units;
    }

    public boolean withdraw(UUID playerId, int units, AuctionDefinitionSettings definition) {
        Player player = Bukkit.getPlayer(playerId);
        Material material = resolveMaterial(definition);
        if (player == null || material == null || units <= 0) {
            return false;
        }
        int need = units;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && need > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int take = Math.min(need, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[slot] = null;
            }
            need -= take;
        }
        player.getInventory().setStorageContents(contents);
        return need == 0;
    }

    public boolean deposit(UUID playerId, int units, AuctionDefinitionSettings definition) {
        Player player = Bukkit.getPlayer(playerId);
        Material material = resolveMaterial(definition);
        if (player == null || material == null || units <= 0) {
            return false;
        }
        ItemStack stack = new ItemStack(material, units);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        return leftovers.isEmpty();
    }

    public String format(int units, AuctionDefinitionSettings definition) {
        Material material = resolveMaterial(definition);
        String name = material == null ? "ITEM" : material.name();
        return units + "x " + name;
    }

    private Material resolveMaterial(AuctionDefinitionSettings definition) {
        if (definition.itemCurrencyMaterial == null || definition.itemCurrencyMaterial.isBlank()) {
            return null;
        }
        return Material.matchMaterial(definition.itemCurrencyMaterial.trim());
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }
}
