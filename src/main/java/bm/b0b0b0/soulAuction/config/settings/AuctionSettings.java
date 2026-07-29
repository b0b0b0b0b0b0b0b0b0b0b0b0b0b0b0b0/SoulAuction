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

    public AuctionSettings() {
        super(SoulAuctionSerializerConfig.INSTANCE);
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
    }
}
