package bm.b0b0b0.soulAuction.config.settings;

import bm.b0b0b0.soulAuction.config.FakeActivityDefaults;
import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class FakeActivitySellersSettings extends YamlSerializable {

    @Comment({
            @CommentValue("Display names shown as sellers (max 16 chars each)."),
            @CommentValue("New nicks can be added here automatically — see fake-activity/settings.yml admin-fake."),
            @CommentValue("Skins: config.yml seller-skins.source (SkinsRestorer or Mojang by nickname)."),
    })
    public List<String> names = FakeActivityDefaults.defaultSellerNames();

    public FakeActivitySellersSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
