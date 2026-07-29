package bm.b0b0b0.soulAuction.service;

import bm.b0b0b0.soulAuction.config.settings.MaterialRuleSettings;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class MaterialRuleMatcher {

    private MaterialRuleMatcher() {
    }

    public static MaterialRuleSettings match(ItemStack item, List<MaterialRuleSettings> globalRules, List<MaterialRuleSettings> auctionRules) {
        MaterialRuleSettings fromAuction = matchList(item, auctionRules);
        if (fromAuction != null) {
            return fromAuction;
        }
        return matchList(item, globalRules);
    }

    private static MaterialRuleSettings matchList(ItemStack item, List<MaterialRuleSettings> rules) {
        if (item == null || item.isEmpty() || rules == null) {
            return null;
        }
        String materialName = item.getType().name();
        MaterialRuleSettings wildcard = null;
        for (MaterialRuleSettings rule : rules) {
            if (rule == null || rule.material == null) {
                continue;
            }
            String pattern = rule.material.trim().toUpperCase();
            if (pattern.endsWith("*")) {
                String prefix = pattern.substring(0, pattern.length() - 1);
                if (materialName.startsWith(prefix)) {
                    wildcard = rule;
                }
                continue;
            }
            if (materialName.equalsIgnoreCase(pattern)) {
                return rule;
            }
        }
        return wildcard;
    }
}
