package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class AuctionDefinitionSettings extends YamlSerializable {

    @Comment({
            @CommentValue("New auction house: copy this file to auctions/<id>.yml, then set id, display-name,"),
            @CommentValue("open/buy/sell-permission (soulauction.*.<id>), economy if needed, grant perms — /ah reload."),
            @CommentValue("id must be unique. Optional: config.yml default-auction-id for /ah without args."),
    })
    public String id = "global";

    @Comment({@CommentValue("Display name in GUI title ({auction} in lang strings)")})
    public String displayName = "Global";

    @Comment({
            @CommentValue("How buyers pay and sellers get paid for listings in THIS auction file."),
            @CommentValue("Requires the matching plugin on the server before you change the value."),
            @CommentValue(""),
            @CommentValue("VAULT — server money through Vault (EssentialsX, CMI, …). Default."),
            @CommentValue("PLAYER_POINTS — PlayerPoints plugin."),
            @CommentValue("EXPERIENCE — Minecraft levels as currency (online payout limitations apply)."),
            @CommentValue("ITEM — price is count of item-currency-material (e.g. emeralds)."),
            @CommentValue("COINS_ENGINE — balance from CoinsEngine plugin:"),
            @CommentValue("  config.yml → coins-engine-currency (default id)"),
            @CommentValue("  this file → coins-engine-currency (override, optional)"),
            @CommentValue("  id must exist in CoinsEngine configuration"),
            @CommentValue(""),
            @CommentValue("Examples:"),
            @CommentValue("  economy: VAULT"),
            @CommentValue("  economy: ITEM  +  item-currency-material: DIAMOND"),
            @CommentValue("  economy: COINS_ENGINE  +  coins-engine-currency: gold"),
    })
    public String economy = "VAULT";

    @Comment({
            @CommentValue("Currency symbol in GUI and chat for this auction"),
            @CommentValue("Empty = economy plugin format (Vault $ etc.)"),
            @CommentValue("Supports MiniMessage: unicode, colors, ItemsAdder/Nexo glyphs, e.g. <glyph:coin> or <font:myfont:a>")
    })
    public String currencySymbol = "₽";

    @Comment({@CommentValue("Place symbol BEFORE or AFTER the amount")})
    public String currencySymbolPosition = "AFTER";

    @Comment({
            @CommentValue("Resolve PlaceholderAPI (%...%) in currencySymbol for the player who sees the price"),
            @CommentValue("Requires PlaceholderAPI; ignored when viewer is unknown (console, offline)")
    })
    public boolean currencySymbolPlaceholderApi = false;

    @Comment({
            @CommentValue("Additional payment economies allowed in same auction house browser"),
            @CommentValue("Example: VAULT, PLAYER_POINTS, ITEM")
    })
    public List<String> allowedEconomies = List.of();
    @Comment({@CommentValue("Bukkit material used when economy is ITEM (price = item count)")})
    public String itemCurrencyMaterial = "EMERALD";
    @Comment({@CommentValue("Tax mode: FLAT, VAT (buyer pays extra), CAPITALISM (high-price surcharge)")})
    public String taxMode = "FLAT";
    @Comment({@CommentValue("For CAPITALISM mode: extra tax percent when price exceeds threshold")})
    public double capitalismThreshold = 10000.0D;
    @Comment({@CommentValue("For CAPITALISM tax mode: extra seller tax % when price is above capitalism-threshold")})
    public double capitalismSurchargePercent = 5.0D;
    @Comment({
            @CommentValue("Per-material price/tax overrides for this auction only (same fields as config.yml material-rules)."),
            @CommentValue("Merged with global material-rules from config.yml."),
    })
    public List<MaterialRuleSettings> materialRules = List.of();
    @Comment({
            @CommentValue("Custom item plugin rules for this auction."),
            @CommentValue("Same structure as custom-item-rules in config.yml (plugin-namespace, sell-allowed, keys, …)."),
            @CommentValue("Merged with global rules. Leave [] to use only config.yml entries."),
    })
    public List<CustomItemPluginRuleSettings> customItemRules = List.of();
    @Comment({@CommentValue("Enable bid listings (experimental)")})
    public boolean bidsEnabled = false;
    @Comment({@CommentValue("Enable rent listings (experimental)")})
    public boolean rentEnabled = false;
    @Comment({
            @CommentValue("CoinsEngine currency id for this auction (only when economy: COINS_ENGINE)."),
            @CommentValue("Leave empty to use coins-engine-currency from main config.yml."),
            @CommentValue("Must match an id from CoinsEngine — not a material name and not Vault."),
    })
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

    @Comment({@CommentValue("Listing lifetime in seconds before auto-expire; 0 or less = no expiry")})
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
            @CommentValue("Placeholders: {seller}, {price}, {id}, {auction}, {expires_in}, {expires_at}")
    })
    public List<String> listingLoreTemplate = List.of();

    public AuctionDefinitionSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }
}
