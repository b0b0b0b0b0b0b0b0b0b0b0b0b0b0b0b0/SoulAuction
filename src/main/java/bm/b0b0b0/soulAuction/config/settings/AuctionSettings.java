package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class AuctionSettings extends YamlSerializable {

    @Comment({@CommentValue("Global limits and safety options")})
    public LimitsSettings limits = new LimitsSettings();

    @Comment({@CommentValue("Default auction used by /ah and /ah sell <price>")})
    public String defaultAuctionId = "global";

    @Comment({@CommentValue("Folder with per-auction files, example: auctions/global.yml")})
    public String auctionsDirectory = "auctions";

    @NewLine
    @Comment({@CommentValue("Storage backend settings")})
    public StorageSettings storage = new StorageSettings();

    @NewLine
    @Comment({@CommentValue("Command aliases redirected to /ah")})
    public List<String> commandAliases = List.of("ax", "auction");

    @NewLine
    @Comment({@CommentValue("Discord and Telegram notifications (async HTTP, no extra libraries)")})
    public NotificationsSettings notifications = new NotificationsSettings();

    @NewLine
    @Comment({@CommentValue("Sell restrictions, cooldowns and player blacklist")})
    public SecuritySettings security = new SecuritySettings();

    @NewLine
    @Comment({@CommentValue("In-game broadcast on large sales")})
    public AnnouncementSettings announcements = new AnnouncementSettings();

    @NewLine
    @Comment({@CommentValue("CoinsEngine default currency id when economy is COINS_ENGINE")})
    public String coinsEngineCurrency = "coins";

    @NewLine
    @Comment({@CommentValue("Multi-server and redis listing sync")})
    public NetworkSettings network = new NetworkSettings();

    @NewLine
    @Comment({@CommentValue("Feature toggles")})
    public FeatureSettings features = new FeatureSettings();

    @NewLine
    @Comment({@CommentValue("Global custom item plugin rules (merged with per-auction)")})
    public List<CustomItemPluginRuleSettings> customItemRules = defaultCustomItemRules();

    @NewLine
    @Comment({@CommentValue("Global per-material rules (merged with per-auction)")})
    public List<MaterialRuleSettings> materialRules = List.of();

    public AuctionSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
    }

    private static List<CustomItemPluginRuleSettings> defaultCustomItemRules() {
        CustomItemPluginRuleSettings executableItems = new CustomItemPluginRuleSettings();
        executableItems.pluginNamespace = "executableitems";
        executableItems.category = "WEAPONS";
        executableItems.searchAliases = List.of("executableitems", "executable", "ei");
        return List.of(new CustomItemPluginRuleSettings(), executableItems);
    }

    public static final class LimitsSettings {

        @Comment({@CommentValue("Default per-auction listing limit if dynamic permission is missing")})
        public int defaultMaxActiveListingsPerAuction = 3;
        @Comment({@CommentValue("Default global listing limit if dynamic permission is missing")})
        public int defaultMaxActiveListingsGlobal = 6;
        @Comment({@CommentValue("Minimum allowed price for one listing")})
        public int minPrice = 1;
        @Comment({@CommentValue("Maximum allowed price for one listing")})
        public int maxPrice = 500000;
        @Comment({@CommentValue("Allow player to buy their own listing")})
        public boolean allowSelfBuy = false;
        @Comment({@CommentValue("Master switch for listing creation")})
        public boolean allowSelling = true;
        @Comment({@CommentValue("How often expired listings are checked, seconds")})
        public int expireCheckIntervalSeconds = 30;
        @Comment({@CommentValue("Auto-deposit money to online sellers; offline uses pending money claim")})
        public boolean autoClaimMoneyWhenOnline = true;
    }

    public static final class NetworkSettings {

        @Comment({@CommentValue("Unique id of this server in a network")})
        public String serverId = "server-1";
        @Comment({@CommentValue("Publish full listing JSON on redis (requires redis enabled)")})
        public boolean redisFullListingSync = true;
    }

    public static final class FeatureSettings {

        @Comment({@CommentValue("Use LuckPerms API for offline seller tax/limit when available")})
        public boolean luckPermsOfflinePermissions = true;
        @Comment({@CommentValue("Advanced search regex (ProtocolLib not required)")})
        public boolean advancedSearchRegex = false;
        @Comment({@CommentValue("Enable message keys disable list in messages.yml under disabled-messages")})
        public boolean respectDisabledMessages = true;
        @Comment({@CommentValue("Cache pre-sorted listing lists per auction (recommended for 10k+ lots)")})
        public boolean preSortedBrowseCache = true;
    }

    public static final class StorageSettings {

        @Comment({
                @CommentValue("Storage mode"),
                @CommentValue("JSON - one .json per listing"),
                @CommentValue("YAML - one .yml per listing"),
                @CommentValue("SQLITE - sqlite database file"),
                @CommentValue("MYSQL - mysql database")
        })
        public String mode = "JSON";
        @Comment({@CommentValue("Folder for flat-file modes")})
        public String flatDirectory = "data/listings";
        @NewLine
        @Comment({@CommentValue("Database settings for SQLITE and MYSQL")})
        public DatabaseSettings database = new DatabaseSettings();
        @NewLine
        @Comment({@CommentValue("Redis guard for MYSQL anti-dupe sell lock")})
        public RedisSettings redis = new RedisSettings();
    }

    public static final class DatabaseSettings {

        @Comment({@CommentValue("MYSQL host")})
        public String host = "127.0.0.1";
        @Comment({@CommentValue("MYSQL port")})
        public int port = 3306;
        @Comment({@CommentValue("MYSQL database name")})
        public String database = "soulauction";
        @Comment({@CommentValue("MYSQL username")})
        public String username = "root";
        @Comment({@CommentValue("MYSQL password")})
        public String password = "password";
        @Comment({@CommentValue("Connection pool size for SQL")})
        public int poolSize = 8;
        @Comment({@CommentValue("SQLite file path relative to plugin folder")})
        public String sqliteFile = "data/auction.db";
    }

    public static final class RedisSettings {

        @Comment({@CommentValue("Enable redis sell lock (applies when storage mode is MYSQL)")})
        public boolean enabled = false;
        @Comment({@CommentValue("Redis host")})
        public String host = "127.0.0.1";
        @Comment({@CommentValue("Redis port")})
        public int port = 6379;
        @Comment({@CommentValue("Redis password, empty for none")})
        public String password = "";
        @Comment({@CommentValue("Redis database index")})
        public int database = 0;
        @Comment({@CommentValue("Redis timeout in ms")})
        public int timeoutMs = 1500;
        @Comment({@CommentValue("Sell lock TTL in ms")})
        public long sellLockMillis = 2500L;
        @NewLine
        @Comment({@CommentValue("Pub/sub channel for cache invalidation across servers (MYSQL + redis)")})
        public boolean pubSubEnabled = false;
        @Comment({@CommentValue("Redis channel name")})
        public String pubSubChannel = "soulauction:cache";
    }

    public static final class SecuritySettings {

        @Comment({@CommentValue("Seconds between listing creations per player, 0 disables")})
        public int sellCooldownSeconds = 0;
        @Comment({@CommentValue("World names where selling is forbidden")})
        public List<String> blockedSellWorlds = List.of();
        @Comment({
                @CommentValue("If true, only materials listed in auction allowedMaterials may be sold"),
                @CommentValue("When false, blacklist mode uses blockedMaterials per auction")
        })
        public boolean materialWhitelistMode = false;
        @Comment({@CommentValue("UUIDs blocked from selling (config); admins can extend at runtime")})
        public List<String> playerBlacklist = List.of();
    }

    public static final class AnnouncementSettings {

        @Comment({@CommentValue("Broadcast to server when a listing is sold")})
        public boolean enabled = true;
        @Comment({@CommentValue("Minimum sale price to announce, 0 = all sales")})
        public int minPrice = 5000;
    }

    public static final class NotificationsSettings {

        @Comment({@CommentValue("Send when a listing is sold")})
        public boolean notifySold = true;
        @Comment({@CommentValue("Send when a new listing is created")})
        public boolean notifyListed = false;
        @Comment({@CommentValue("Send when a listing expires")})
        public boolean notifyExpired = true;
        @Comment({@CommentValue("Only notify if price is at least this value, 0 = all")})
        public int minPrice = 0;
        @NewLine
        @Comment({@CommentValue("Discord incoming webhook")})
        public DiscordNotificationSettings discord = new DiscordNotificationSettings();
        @NewLine
        @Comment({@CommentValue("Telegram Bot API (@BotFather token + chat id)")})
        public TelegramNotificationSettings telegram = new TelegramNotificationSettings();
    }

    public static final class DiscordNotificationSettings {

        @Comment({@CommentValue("Enable Discord webhook notifications")})
        public boolean enabled = false;
        @Comment({@CommentValue("Full webhook URL from Discord channel integrations")})
        public String webhookUrl = "";
        @Comment({
                @CommentValue("Show Minecraft player heads in embed (author + thumbnail)"),
                @CommentValue("Uses public avatar CDN (Crafatar / Minotar), server needs outbound HTTPS")
        })
        public boolean showPlayerAvatars = true;
        @Comment({
                @CommentValue("Avatar CDN"),
                @CommentValue("MINOTAR - minotar.net (default)"),
                @CommentValue("CRAFATAR - crafatar.com by UUID")
        })
        public String avatarProvider = "MINOTAR";
    }

    public static final class TelegramNotificationSettings {

        @Comment({@CommentValue("Enable Telegram notifications")})
        public boolean enabled = false;
        @Comment({@CommentValue("Bot token from @BotFather")})
        public String botToken = "";
        @Comment({@CommentValue("Chat id: user, group or channel (often negative for groups)")})
        public String chatId = "";
    }
}
