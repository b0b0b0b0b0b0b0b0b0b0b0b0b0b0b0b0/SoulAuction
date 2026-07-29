package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class CustomItemPluginRuleSettings extends YamlSerializable {

    public String pluginNamespace = "itemsadder";

    public boolean sellAllowed = true;

    public List<String> blockedKeys = List.of();

    public String category = "";

    public List<String> searchAliases = List.of();

    public List<CustomItemKeyRuleSettings> keys = List.of();

    public CustomItemPluginRuleSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
