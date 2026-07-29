package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class CustomItemPluginRuleSettings extends YamlSerializable {

    @Comment({
            @CommentValue("Plugin namespace in PDC, example itemsadder, oraxen, mmoitems"),
            @CommentValue("Set sellAllowed false to block all items from this namespace")
    })
    public String pluginNamespace = "itemsadder";

    @Comment({@CommentValue("Allow selling items detected from this plugin")})
    public boolean sellAllowed = true;

    @Comment({@CommentValue("Blocked item keys inside namespace (optional)")})
    public List<String> blockedKeys = List.of();

    public CustomItemPluginRuleSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
