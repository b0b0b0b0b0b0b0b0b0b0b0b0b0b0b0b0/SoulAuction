package bm.b0b0b0.soulAuction.config.settings;

import net.elytrium.serializer.language.object.YamlSerializable;

public final class MaterialRuleSettings extends YamlSerializable {

    public String material = "STONE";

    public String customItemId = "";

    public int minPrice = 0;

    public int maxPrice = 0;

    public double saleTaxPercent = -1.0D;

    public double buyTaxPercent = -1.0D;

    public MaterialRuleSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
