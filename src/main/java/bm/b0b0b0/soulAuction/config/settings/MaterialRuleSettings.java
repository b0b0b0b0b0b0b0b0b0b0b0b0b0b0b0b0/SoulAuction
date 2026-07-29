package bm.b0b0b0.soulAuction.config.settings;

import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class MaterialRuleSettings extends YamlSerializable {

    @Comment({@CommentValue("Bukkit Material name or prefix*, example DIAMOND_SWORD")})
    public String material = "STONE";

    @Comment({@CommentValue("Optional custom item plugin id match, example itemsadder:ruby")})
    public String customItemId = "";

    @Comment({@CommentValue("Minimum listing price when this rule matches")})
    public int minPrice = 0;

    @Comment({@CommentValue("Maximum listing price when this rule matches, 0 = ignore")})
    public int maxPrice = 0;

    @Comment({@CommentValue("Sale tax percent override for this material")})
    public double saleTaxPercent = -1.0D;

    @Comment({@CommentValue("Buy tax percent override for this material")})
    public double buyTaxPercent = -1.0D;

    public MaterialRuleSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
