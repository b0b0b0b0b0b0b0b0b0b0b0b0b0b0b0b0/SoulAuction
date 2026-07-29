package bm.b0b0b0.soulAuction.service.customitem;

import bm.b0b0b0.soulAuction.config.settings.CustomItemKeyRuleSettings;
import bm.b0b0b0.soulAuction.config.settings.CustomItemPluginRuleSettings;
import bm.b0b0b0.soulAuction.model.AuctionCategory;
import bm.b0b0b0.soulAuction.util.CustomItemIdentity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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
            CustomItemPluginRuleSettings namespaceRule = findRule(rules, ref.namespace());
            CustomItemKeyRuleSettings keyRule = findKeyRule(namespaceRule, ref.key());
            if (keyRule != null && keyRule.sellAllowed != null && !keyRule.sellAllowed.isBlank()) {
                if (!Boolean.parseBoolean(keyRule.sellAllowed.trim())) {
                    return false;
                }
                continue;
            }
            if (namespaceRule == null) {
                continue;
            }
            if (!namespaceRule.sellAllowed) {
                return false;
            }
            if (namespaceRule.blockedKeys != null) {
                for (String blocked : namespaceRule.blockedKeys) {
                    if (blocked != null && blocked.equalsIgnoreCase(ref.key())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public AuctionCategory resolveCategory(ItemStack item, List<CustomItemPluginRuleSettings> rules, AuctionCategory materialFallback) {
        if (item == null || item.isEmpty() || rules == null || rules.isEmpty()) {
            return materialFallback;
        }
        for (CustomItemIdentity.PluginItemRef ref : CustomItemIdentity.detect(item)) {
            CustomItemPluginRuleSettings namespaceRule = findRule(rules, ref.namespace());
            CustomItemKeyRuleSettings keyRule = findKeyRule(namespaceRule, ref.key());
            AuctionCategory fromKey = parseCategory(keyRule == null ? null : keyRule.category);
            if (fromKey != null) {
                return fromKey;
            }
            if (namespaceRule != null) {
                AuctionCategory fromNamespace = parseCategory(namespaceRule.category);
                if (fromNamespace != null) {
                    return fromNamespace;
                }
            }
        }
        return materialFallback;
    }

    public String searchTokens(ItemStack item, List<CustomItemPluginRuleSettings> rules) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        Set<String> tokens = new LinkedHashSet<>();
        List<CustomItemIdentity.PluginItemRef> refs = CustomItemIdentity.detect(item);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta == null ? null : meta.getPersistentDataContainer();
        for (CustomItemIdentity.PluginItemRef ref : refs) {
            tokens.add(ref.namespace());
            tokens.add(ref.key());
            tokens.add(ref.namespace() + ":" + ref.key());
            String value = readStringValue(container, ref.namespace(), ref.key());
            if (value != null && !value.isBlank()) {
                tokens.add(value.toLowerCase(Locale.ROOT));
            }
            CustomItemPluginRuleSettings namespaceRule = findRule(rules, ref.namespace());
            CustomItemKeyRuleSettings keyRule = findKeyRule(namespaceRule, ref.key());
            addAliases(tokens, keyRule == null ? null : keyRule.searchAliases);
            addAliases(tokens, namespaceRule == null ? null : namespaceRule.searchAliases);
        }
        if (tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            builder.append(' ').append(token.toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    public String displayTag(ItemStack item) {
        return CustomItemIdentity.primaryLabel(item);
    }

    private void addAliases(Set<String> tokens, List<String> aliases) {
        if (aliases == null) {
            return;
        }
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                tokens.add(alias.toLowerCase(Locale.ROOT));
            }
        }
    }

    private AuctionCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AuctionCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String readStringValue(PersistentDataContainer container, String namespace, String key) {
        if (container == null) {
            return null;
        }
        org.bukkit.NamespacedKey namespacedKey = new org.bukkit.NamespacedKey(namespace, key);
        if (!container.has(namespacedKey, PersistentDataType.STRING)) {
            return null;
        }
        return container.get(namespacedKey, PersistentDataType.STRING);
    }

    private CustomItemPluginRuleSettings findRule(List<CustomItemPluginRuleSettings> rules, String namespace) {
        for (CustomItemPluginRuleSettings rule : rules) {
            if (rule.pluginNamespace != null && rule.pluginNamespace.equalsIgnoreCase(namespace)) {
                return rule;
            }
        }
        return null;
    }

    private CustomItemKeyRuleSettings findKeyRule(CustomItemPluginRuleSettings namespaceRule, String itemKey) {
        if (namespaceRule == null || namespaceRule.keys == null || itemKey == null) {
            return null;
        }
        for (CustomItemKeyRuleSettings keyRule : namespaceRule.keys) {
            if (keyRule.itemKey != null && keyRule.itemKey.equalsIgnoreCase(itemKey)) {
                return keyRule;
            }
        }
        return null;
    }
}
