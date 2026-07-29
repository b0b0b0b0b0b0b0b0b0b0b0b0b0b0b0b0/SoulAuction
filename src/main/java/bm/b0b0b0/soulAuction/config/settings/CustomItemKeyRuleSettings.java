package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class CustomItemKeyRuleSettings extends YamlSerializable {

    public String itemKey = "";

    public String category = "";

    public List<String> searchAliases = List.of();

    public String sellAllowed = "";

    public CustomItemKeyRuleSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
