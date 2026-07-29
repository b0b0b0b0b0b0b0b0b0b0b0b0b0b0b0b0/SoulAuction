package bm.b0b0b0.soulAuction.service.customitem;

import bm.b0b0b0.soulAuction.config.settings.CustomItemPluginRuleSettings;
import bm.b0b0b0.soulAuction.util.CustomItemIdentity;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class CustomItemRuleEngine {

    public boolean isSellAllowed(ItemStack item, List<CustomItemPluginRuleSettings> rules) {
        if (item == null || item.isEmpty() || rules == null || rules.isEmpty()) {
            return true;
        }
        List<CustomItemIdentity.PluginItemRef> refs = CustomItemIdentity.detect(item);
        if (refs.isEmpty()) {
            return true;
        }
        for (CustomItemIdentity.PluginItemRef ref : refs) {
            CustomItemPluginRuleSettings rule = findRule(rules, ref.namespace());
            if (rule == null) {
                continue;
            }
            if (!rule.sellAllowed) {
                return false;
            }
            if (rule.blockedKeys != null) {
                for (String blocked : rule.blockedKeys) {
                    if (blocked != null && blocked.equalsIgnoreCase(ref.key())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public String displayTag(ItemStack item) {
        return CustomItemIdentity.primaryLabel(item);
    }

    private CustomItemPluginRuleSettings findRule(List<CustomItemPluginRuleSettings> rules, String namespace) {
        for (CustomItemPluginRuleSettings rule : rules) {
            if (rule.pluginNamespace != null && rule.pluginNamespace.equalsIgnoreCase(namespace)) {
                return rule;
            }
        }
        return null;
    }
}
