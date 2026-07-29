package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class AuctionDefinitionSettings extends YamlSerializable {

    @Comment({@CommentValue("Unique auction id used in commands and permissions")})
    public String id = "global";

    @Comment({@CommentValue("Display name shown in GUI title")})
    public String displayName = "Глобальный";

    @Comment({
            @CommentValue("Economy mode for this auction"),
            @CommentValue("VAULT - server money via Vault"),
            @CommentValue("PLAYER_POINTS - points via PlayerPoints"),
            @CommentValue("EXPERIENCE - player levels (online payout only)"),
            @CommentValue("COINS_ENGINE - CoinsEngine currency")
    })
    public String economy = "VAULT";
    @Comment({@CommentValue("CoinsEngine currency for this auction when economy is COINS_ENGINE")})
    public String coinsEngineCurrency = "";

    @Comment({@CommentValue("Allow buying in this auction")})
    public boolean buyEnabled = true;

    @Comment({@CommentValue("Allow listing items in this auction")})
    public boolean sellEnabled = true;

    @Comment({@CommentValue("Permission to open this auction GUI")})
    public String openPermission = "soulauction.open.global";

    @Comment({@CommentValue("Permission to buy listings in this auction")})
    public String buyPermission = "soulauction.buy.global";

    @Comment({@CommentValue("Permission to create listings in this auction")})
    public String sellPermission = "soulauction.sell.global";

    @Comment({@CommentValue("Listing lifetime in seconds before auto-expire")})
    public int listingTtlSeconds = 86400;

    @Comment({@CommentValue("Tax percent taken from seller payout, example: 5.0")})
    public double saleTaxPercent = 0.0D;

    @Comment({@CommentValue("Tax percent added to buyer payment, example: 2.0")})
    public double buyTaxPercent = 0.0D;

    @Comment({@CommentValue("Minimum listing price for this auction, 0 uses global min")})
    public int minPrice = 0;

    @Comment({@CommentValue("Maximum listing price for this auction, 0 uses global max")})
    public int maxPrice = 0;

    @Comment({
            @CommentValue("Blocked materials for selling in this auction"),
            @CommentValue("Use Bukkit Material names")
    })
    public List<String> blockedMaterials = List.of("BEDROCK", "BARRIER");

    @Comment({
            @CommentValue("Allowed materials when global security.materialWhitelistMode is true"),
            @CommentValue("Empty list means nothing can be sold in whitelist mode")
    })
    public List<String> allowedMaterials = List.of();

    @Comment({
            @CommentValue("Optional MiniMessage lore lines appended to listing item in browser GUI"),
            @CommentValue("Placeholders: {seller}, {price}, {id}, {auction}")
    })
    public List<String> listingLoreTemplate = List.of();

    public AuctionDefinitionSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
