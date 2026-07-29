package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class CustomItemKeyRuleSettings extends YamlSerializable {

    @Comment({@CommentValue("PDC key inside plugin namespace, example my_sword")})
    public String itemKey = "";

    @Comment({@CommentValue("Auction category override: WEAPONS, TOOLS, ARMOR, OTHER, ...")})
    public String category = "";

    @Comment({@CommentValue("Extra search tokens for this key")})
    public List<String> searchAliases = List.of();

    @Comment({@CommentValue("Override sell allow for this key; empty = use namespace rule")})
    public String sellAllowed = "";

    public CustomItemKeyRuleSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
