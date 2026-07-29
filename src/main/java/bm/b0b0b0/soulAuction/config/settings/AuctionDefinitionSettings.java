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
            @CommentValue("PLAYER_POINTS - points via PlayerPoints")
    })
    public String economy = "VAULT";

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

    @Comment({@CommentValue("Tax percent taken from sale, example: 5.0")})
    public double saleTaxPercent = 0.0D;

    @Comment({
            @CommentValue("Blocked materials for selling in this auction"),
            @CommentValue("Use Bukkit Material names")
    })
    public List<String> blockedMaterials = List.of("BEDROCK", "BARRIER");

    public AuctionDefinitionSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
