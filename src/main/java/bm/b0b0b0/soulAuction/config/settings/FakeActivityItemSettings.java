package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class FakeActivityItemSettings extends YamlSerializable {

    public String id = "item";

    public String material = "STONE";

    public int amount = 1;

    public String itemBase64 = "";

    public int minPrice = 0;

    public int maxPrice = 0;

    public List<String> auctionIds = List.of();

    public int weight = 1;

    public FakeActivityItemSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }

    public boolean appliesToAuction(String auctionId) {
        if (auctionIds == null || auctionIds.isEmpty()) {
            return true;
        }
        for (String allowed : auctionIds) {
            if (allowed != null && allowed.equalsIgnoreCase(auctionId)) {
                return true;
            }
        }
        return false;
    }
}
