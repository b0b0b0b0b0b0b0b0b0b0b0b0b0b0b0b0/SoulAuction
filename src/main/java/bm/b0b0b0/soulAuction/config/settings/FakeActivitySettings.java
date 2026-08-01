package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class FakeActivitySettings extends YamlSerializable {

    @NewLine
    @Comment({
            @CommentValue("/ah admin fake — optionally save nick and hand item into fake-activity pool."),
    })
    public AdminFakeSettings adminFake = new AdminFakeSettings();

    @Comment({
            @CommentValue("How many fake listings to create immediately when enabled (0 = up to max-total-listings)."),
    })
    public int initialFillListings = 0;

    @Comment({
            @CommentValue("Delay before periodic top-up ticks after startup (seconds)."),
    })
    public int initialDelaySeconds = 30;

    @Comment({
            @CommentValue("How often to try adding listings when below limits (seconds)."),
    })
    public int tickIntervalSeconds = 90;

    @Comment({
            @CommentValue("After a fake listing is bought, wait this long before listing another item (seconds)."),
    })
    public int afterPurchaseDelaySeconds = 180;

    @NewLine
    @Comment({
            @CommentValue("Default price range when an item file leaves min/max at 0."),
    })
    public int minPrice = 100;

    @Comment({
            @CommentValue("Default maximum price."),
    })
    public int maxPrice = 25000;

    @Comment({
            @CommentValue("Random +/- percent applied to the chosen price."),
    })
    public int priceVariancePercent = 20;

    @NewLine
    @Comment({
            @CommentValue("Cap fake listings per auction house file (auctions/*.yml id)."),
    })
    public int maxListingsPerAuction = 15;

    @Comment({
            @CommentValue("Cap fake listings across all auctions."),
    })
    public int maxTotalListings = 45;

    @Comment({
            @CommentValue("Randomly backdate fake listing created-at by up to this many seconds."),
            @CommentValue("Makes expiry timers differ after bootstrap (0 = same moment for all)."),
            @CommentValue("Capped by auction listing TTL so lots do not spawn already expired."),
    })
    public int listingAgeSpreadSeconds = 86400;

    @Comment({
            @CommentValue("How many listings to attempt each tick (until caps are reached)."),
    })
    public int listingsPerTick = 2;

    @NewLine
    @Comment({
            @CommentValue("Auction ids to fill (empty = every auction with fake-activity-enabled in auctions/*.yml)."),
            @CommentValue("Example: [ global, resources ]"),
    })
    public List<String> auctionIds = List.of();

    public static final class AdminFakeSettings {

        @Comment({
                @CommentValue("Append seller nick to fake-activity/sellers.yml after a successful /ah admin fake."),
        })
        public boolean registerSeller = true;

        @Comment({
                @CommentValue("Append hand item to fake-activity/items.yml after a successful /ah admin fake."),
        })
        public boolean registerItem = true;
    }

    public FakeActivitySettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
