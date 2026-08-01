package bm.b0b0b0.soulAuction.config.settings;

import bm.b0b0b0.soulAuction.config.FakeActivityDefaults;
import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class FakeActivityItemsSettings extends YamlSerializable {

    @Comment({
            @CommentValue("Pool of items fake sellers can list. One random entry is picked each time."),
            @CommentValue("Hand items from /ah admin fake can be appended here — see settings.yml admin-fake."),
            @CommentValue(""),
            @CommentValue("Fields per entry:"),
            @CommentValue("  id — unique name (for your reference)"),
            @CommentValue("  material — Bukkit material (ignored when item-base64 is set)"),
            @CommentValue("  amount — stack size"),
            @CommentValue("  item-base64 — optional full item snapshot"),
            @CommentValue("  min-price / max-price — 0 uses fake-activity/settings.yml defaults"),
            @CommentValue("  auction-ids — empty = any auction; else only those auction ids"),
            @CommentValue("  weight — relative pick chance (higher = more often)"),
    })
    public List<FakeActivityItemSettings> items = FakeActivityDefaults.defaultItems();

    public FakeActivityItemsSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
