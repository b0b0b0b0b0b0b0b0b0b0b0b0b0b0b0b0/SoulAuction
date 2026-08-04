package bm.b0b0b0.soulAuction.config.settings;

import java.util.List;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;

public final class AuctionSettings extends YamlSerializable {

    @Comment({
            @CommentValue("=== Update check ==="),
            @CommentValue("On startup (async): fetch latest version from https://b0b0b0.dev/pl/souls/soulauction.txt"),
            @CommentValue("and print result to console. No player or server data is sent."),
            @CommentValue("Set false to disable the remote version check entirely."),
    })
    public boolean checkForUpdates = true;

    @NewLine
    @Comment({
            @CommentValue("=== bStats (anonymous usage statistics) ==="),
            @CommentValue("Helps the author see how many servers run SoulAuction: plugin version."),
            @CommentValue("Set enabled: false to opt out on this server."),
    })
    public BstatsSettings bstats = new BstatsSettings();

    @NewLine
    @Comment({@CommentValue("Global limits and safety options")})
    public LimitsSettings limits = new LimitsSettings();

    @Comment({
            @CommentValue("Default auction id for /ah and /ah sell <price> when no auction is specified."),
            @CommentValue("Must match id: in a file under auctions-directory (default auctions/global.yml)."),
    })
    public String defaultAuctionId = "global";

    @Comment({
            @CommentValue("Subfolder with one YAML per auction house."),
            @CommentValue("Example: auctions/global.yml → auction id global."),
    })
    public String auctionsDirectory = "auctions";

    @NewLine
    @Comment({@CommentValue("Storage backend settings")})
    public StorageSettings storage = new StorageSettings();

    @NewLine
    @Comment({
            @CommentValue("Extra commands that run the same as /ah (register in plugin.yml + here)."),
            @CommentValue("Example: players can type /auction or /ax instead of /ah."),
    })
    public List<String> commandAliases = List.of("ax", "auction");

    @NewLine
    @Comment({@CommentValue("Discord and Telegram notifications (async HTTP, no extra libraries)")})
    public NotificationsSettings notifications = new NotificationsSettings();

    @NewLine
    @Comment({
            @CommentValue("Cooldowns, blocked worlds, sell material policy, sell blacklist."),
            @CommentValue("Material lists per auction: auctions/*.yml → blocked-materials / allowed-materials."),
    })
    public SecuritySettings security = new SecuritySettings();

    @NewLine
    @Comment({
            @CommentValue("Server-wide chat broadcasts (texts in lang: announce-item-*, region-announce-*)."),
            @CommentValue("Each event has its own toggle. min-*-price: 0 = announce every deal."),
            @CommentValue("Old keys announcements.enabled / min-price → use items.broadcast-purchase / min-purchase-price."),
    })
    public AnnouncementSettings announcements = new AnnouncementSettings();

    @NewLine
    @Comment({
            @CommentValue("=== CoinsEngine (optional third-party plugin) ==="),
            @CommentValue("Only applies when an auction uses economy: COINS_ENGINE (see auctions/*.yml)."),
            @CommentValue(""),
            @CommentValue("CoinsEngine adds extra player balances (coins, tokens, etc.). Each balance"),
            @CommentValue("has an id configured inside CoinsEngine — SoulAuction does not create currencies."),
            @CommentValue("On buy/sell SoulAuction adds/removes that balance by id."),
            @CommentValue(""),
            @CommentValue("This field = server-wide DEFAULT currency id when an auction leaves"),
            @CommentValue("coins-engine-currency empty in its YAML. Override per auction there."),
            @CommentValue(""),
            @CommentValue("Setup: install CoinsEngine → create currency → copy its id here →"),
            @CommentValue("in auctions/global.yml set economy: COINS_ENGINE (and optional coins-engine-currency)."),
            @CommentValue(""),
            @CommentValue("Vault-only server? Value is ignored; leave coins or any placeholder."),
    })
    public String coinsEngineCurrency = "coins";

    @NewLine
    @Comment({
            @CommentValue("Multi-server identity (BungeeCord / Velocity network with shared MYSQL)."),
            @CommentValue("Redis listing sync options: storage → redis in config.yml."),
    })
    public NetworkSettings network = new NetworkSettings();

    @NewLine
    @Comment({@CommentValue("Feature toggles")})
    public FeatureSettings features = new FeatureSettings();

    @NewLine
    @Comment({
            @CommentValue("WorldGuard region marketplace (requires WorldGuard plugin)."),
            @CommentValue("On by default — /ah reload toggles without full restart."),
            @CommentValue("When off or WG missing: no listeners, no commands."),
            @CommentValue(""),
            @CommentValue("NOT the same as world-guard-trade-regions in auctions/*.yml:"),
            @CommentValue("  region-market here = sell/buy WorldGuard regions as auction lots (/ah regions)."),
            @CommentValue("  world-guard-trade-regions = limit where players trade ITEMS in a given auction."),
    })
    public RegionMarketSettings regionMarket = new RegionMarketSettings();

    @NewLine
    @Comment({
            @CommentValue("Seller skull textures in GUI (favorite sellers, etc.)."),
    })
    public SellerSkinSettings sellerSkins = new SellerSkinSettings();

    @NewLine
    @Comment({
            @CommentValue("Fake activity pool (sellers, items, timers) — see fake-activity.directory."),
            @CommentValue("Enable fake listings per auction in auctions/*.yml → fake-activity-enabled."),
    })
    public FakeActivityRootSettings fakeActivity = new FakeActivityRootSettings();

    @NewLine
    public MessagesSettings messages = new MessagesSettings();

    @NewLine
    @Comment({
            @CommentValue("Custom item plugins (ItemsAdder, Oraxen, MMOItems, ExecutableItems, …)."),
            @CommentValue("Merged with custom-item-rules in each auctions/*.yml file."),
            @CommentValue("Items are detected via PersistentDataContainer namespace on the stack."),
            @CommentValue(""),
            @CommentValue("Fields per list entry (see defaults below):"),
            @CommentValue("  plugin-namespace — namespace id, e.g. itemsadder, oraxen, executableitems"),
            @CommentValue("  sell-allowed — false blocks all items from that plugin (unless keys override)"),
            @CommentValue("  blocked-keys — item keys still forbidden when sell-allowed is true"),
            @CommentValue("  category — WEAPONS, TOOLS, ARMOR, OTHER, … for browse category filter"),
            @CommentValue("  search-aliases — extra tokens for search"),
            @CommentValue("  keys — optional nested list (per item id inside that namespace):"),
            @CommentValue("    item-key — plugin item id"),
            @CommentValue("    category — WEAPONS, TOOLS, ARMOR, OTHER, …"),
            @CommentValue("    search-aliases — extra search tokens for that item"),
            @CommentValue("    sell-allowed — \"true\", \"false\", or empty to inherit namespace sell-allowed"),
            @CommentValue(""),
            @CommentValue("Example namespace + keys:"),
            @CommentValue("  - plugin-namespace: itemsadder"),
            @CommentValue("    sell-allowed: true"),
            @CommentValue("    keys:"),
            @CommentValue("      - item-key: my_custom_sword"),
            @CommentValue("        category: WEAPONS"),
            @CommentValue("        sell-allowed: \"true\""),
            @CommentValue(""),
            @CommentValue("Example second plugin (no keys):"),
            @CommentValue("  - plugin-namespace: oraxen"),
            @CommentValue("    sell-allowed: true"),
            @CommentValue("    category: WEAPONS"),
    })
    public List<CustomItemPluginRuleSettings> customItemRules = defaultCustomItemRules();

    @NewLine
    @Comment({
            @CommentValue("Per-material price and tax overrides (merged with material-rules in auctions/*.yml)."),
            @CommentValue("First matching rule wins when a player lists that material."),
            @CommentValue(""),
            @CommentValue("Fields per entry:"),
            @CommentValue("  material — Bukkit Material name (DIAMOND_SWORD, …)"),
            @CommentValue("  custom-item-id — optional ItemsAdder-style id instead of vanilla material"),
            @CommentValue("  min-price / max-price — listing price bounds for this match (0 max = ignore)"),
            @CommentValue("  sale-tax-percent / buy-tax-percent — override auction tax; -1 = use auction default"),
            @CommentValue(""),
            @CommentValue("Example:"),
            @CommentValue("  - material: DIAMOND"),
            @CommentValue("    min-price: 100"),
            @CommentValue("    sale-tax-percent: 2.5"),
    })
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

    public static final class BstatsSettings {

        @Comment({@CommentValue("Send anonymous metrics to https://bstats.org")})
        public boolean enabled = true;
    }

    public static final class LimitsSettings {

        @Comment({
                @CommentValue("Max active listings per player in ONE auction if no permission override."),
                @CommentValue("Higher limit via permission soulauction.<auctionId>.<number>"),
                @CommentValue("Example: soulauction.global.10 allows 10 lots on auction global."),
        })
        public int defaultMaxActiveListingsPerAuction = 3;
        @Comment({
                @CommentValue("Max active listings per player ACROSS ALL auctions combined."),
                @CommentValue("Override via permission soulauction.all.<number> (e.g. soulauction.all.20)."),
        })
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

        @Comment({
                @CommentValue("Unique name of THIS backend server in a proxy network."),
                @CommentValue("Stored on new listings (server-origin) so you can tell which shard listed an item."),
                @CommentValue("Example: lobby, survival, skyblock — pick any stable id per machine."),
        })
        public String serverId = "server-1";
    }

    public static final class MessagesSettings {

        @Comment({
                @CommentValue("=== Plugin language (GUI, button lore, chat, command errors) ==="),
                @CommentValue("Strings live in lang/messages_<code>.yml"),
                @CommentValue("The plugin does not translate for you — it picks the matching file."),
                @CommentValue("Edit YAML or copy messages_en.yml to your own messages_xx.yml."),
                @CommentValue("After lang changes: /ah reload (or restart the server)."),
                @CommentValue(""),
                @CommentValue("How to pick the language for players (locale-mode):"),
                @CommentValue(""),
                @CommentValue("CLIENT — default. Uses each player's Minecraft language setting:"),
                @CommentValue("  • lang/messages_<code>.yml exists for the client language → that file"),
                @CommentValue("  • ru / ru_ru → messages_ru.yml (when present)"),
                @CommentValue("  • otherwise → messages_en.yml"),
                @CommentValue("  Good when the server has mixed RU and EN players."),
                @CommentValue(""),
                @CommentValue("SERVER — one language for everyone:"),
                @CommentValue("  • set server-locale below (e.g. ru)"),
                @CommentValue("  • player client language is ignored"),
                @CommentValue("  • everyone sees the same strings from one YAML"),
                @CommentValue(""),
                @CommentValue("Allowed locale-mode: CLIENT or SERVER (case-insensitive)."),
                @CommentValue(""),
                @CommentValue("Server-wide locale (server-locale). Only used when locale-mode: SERVER."),
                @CommentValue("Code = file suffix: lang/messages_<code>.yml"),
                @CommentValue("Bundled in JAR: en, ru. Any messages_*.yml in lang/ is loaded on startup and /ah reload."),
                @CommentValue(""),
                @CommentValue("Example — Russian for everyone:"),
                @CommentValue("  locale-mode: SERVER"),
                @CommentValue("  server-locale: ru"),
                @CommentValue(""),
                @CommentValue("Example — English for everyone:"),
                @CommentValue("  locale-mode: SERVER"),
                @CommentValue("  server-locale: en"),
                @CommentValue(""),
                @CommentValue("Custom locale: add messages_de.yml to lang/ and set server-locale: de"),
                @CommentValue("(server-locale is ignored when locale-mode is CLIENT)."),
                @CommentValue(""),
                @CommentValue("If the locale file is missing, fallback is en."),
        })
        public String localeMode = "CLIENT";

        public String serverLocale = "en";
    }

    public static final class FeatureSettings {

        @Comment({@CommentValue("Use LuckPerms API for offline seller tax/limit when available")})
        public boolean luckPermsOfflinePermissions = true;
        @Comment({@CommentValue("Advanced search regex (ProtocolLib not required)")})
        public boolean advancedSearchRegex = false;
        @Comment({
                @CommentValue("Typo-tolerant search: Levenshtein similarity per word (like chat same-message limiters)"),
                @CommentValue("Example: query \"diamnod\" matches listing text \"diamond\" at 87% threshold"),
        })
        public boolean searchFuzzyEnabled = true;
        @Comment({@CommentValue("Minimum similarity percent (1-100) for fuzzy token match")})
        public int searchFuzzyMinSimilarityPercent = 87;
        @Comment({@CommentValue("Shorter query tokens use exact contains only (fuzzy skipped)")})
        public int searchFuzzyMinTokenLength = 3;
        @Comment({
                @CommentValue("Search index locales — Minecraft lang file codes from server jar (comma-separated)"),
                @CommentValue("Format: language-REGION, e.g. ru-RU, en-US, zu-ZA, de-DE (also ru_RU)"),
                @CommentValue("Bare language (ru) tries ru_ru.json; with region uses exact file zu_za.json"),
        })
        public String searchLocales = "ru,en";
        @Comment({@CommentValue("Search with wrong keyboard layout (QWERTY ↔ Russian JCUKEN)")})
        public boolean searchKeyboardLayoutFix = true;
        @Comment({
                @CommentValue("Enable message keys disable list in lang/messages_*.yml → disabled-messages."),
                @CommentValue("Example placeholder key: example-disabled-key (does nothing; replace with keys like announce-sale)."),
        })
        public boolean respectDisabledMessages = true;
        @Comment({@CommentValue("Cache pre-sorted listing lists per auction (recommended for 10k+ lots)")})
        public boolean preSortedBrowseCache = true;
    }

    public static final class SellerSkinSettings {

        @Comment({
                @CommentValue("Where to load seller head textures from."),
                @CommentValue("On offline-mode (cracked) servers prefer skins-restorer over mojang/auto."),
                @CommentValue("auto — SkinsRestorer when installed, otherwise Mojang API by nickname"),
                @CommentValue("skins-restorer — only SkinsRestorer (recommended for offline-mode)"),
                @CommentValue("mojang — Mojang session server (online-mode; real Mojang nicks only)"),
                @CommentValue("off — default heads, no lookups"),
        })
        public String source = "auto";

        @Comment({
                @CommentValue("SkinsRestorer skin id when lookup by seller nick fails."),
                @CommentValue("Custom skin name from SR, another player nick, or URL already in SR."),
                @CommentValue("Empty = keep Steve/Alex head or use SR defaultSkins via player lookup."),
        })
        public String fallbackSkin = "";

        @Comment({
                @CommentValue("Force this skin for all fake/synthetic seller heads (ignore nick)."),
                @CommentValue("Use a SkinsRestorer custom skin (e.g. server logo). Empty = resolve by nick."),
        })
        public String fakeSellerSkin = "";
    }

    public static final class StorageSettings {

        @Comment({
                @CommentValue("!!! THE SERVER MUST BE COMPLETELY STOPPED BEFORE CHANGING storage.mode !!!"),
                @CommentValue("!!! DO NOT USE /ah reload — SHUT DOWN THE SERVER, EDIT CONFIG, START AGAIN !!!"),
                @CommentValue(""),
                @CommentValue("Where listings are stored on disk / database."),
                @CommentValue("SQLITE — single sqlite file (default; good for most servers)"),
                @CommentValue("JSON — one .json file per listing (tiny servers / debugging only)"),
                @CommentValue("YAML — one .yml file per listing"),
                @CommentValue("MYSQL — shared database (networks, large catalogs); pair with redis sell lock"),
                @CommentValue(""),
                @CommentValue("Changing mode does NOT auto-move data. After switch: start server, then"),
                @CommentValue("/ah admin migrate from JSON|YAML|SQLITE (target = mode above)."),
                @CommentValue("Optional flags: dry-run, archive (renames old flat folder / sqlite file after success)."),
        })
        public String mode = "SQLITE";
        @Comment({
                @CommentValue("Directory for JSON/YAML flat storage, relative to plugin folder."),
                @CommentValue("Ignored when mode is SQLITE or MYSQL."),
                @CommentValue("Same rule as storage.mode: server MUST be fully stopped before changing this path."),
        })
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
        public String sqliteFile = "data/database/auction.db";
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
        @Comment({
                @CommentValue("Cross-server pub/sub (needs redis.enabled + storage mode MYSQL)."),
                @CommentValue("Keeps browse caches in sync when another backend changes listings."),
        })
        public boolean pubSubEnabled = false;
        @Comment({@CommentValue("Redis channel for cache invalidation and listing sync messages")})
        public String pubSubChannel = "soulauction:cache";
        @Comment({
                @CommentValue("Publish full listing JSON on pub/sub when a lot is created, sold, or removed."),
                @CommentValue("Other servers apply it to browse cache without reloading all rows from MYSQL."),
                @CommentValue("Requires redis.enabled and pub-sub-enabled above."),
        })
        public boolean redisFullListingSync = true;
    }

    public static final class SecuritySettings {

        @Comment({
                @CommentValue("Seconds between listing creations per player, 0 disables"),
                @CommentValue("Example: 30"),
        })
        public int sellCooldownSeconds = 0;
        @Comment({
                @CommentValue("World names where selling is forbidden (exact Bukkit world name)"),
                @CommentValue("Example:"),
                @CommentValue("  blocked-sell-worlds:"),
                @CommentValue("    - world_nether"),
                @CommentValue("    - spawn"),
        })
        public List<String> blockedSellWorlds = List.of();
        @Comment({
                @CommentValue("true = sell only materials listed in allowed-materials on each auction file."),
                @CommentValue("false = blacklist mode: blocked-materials on each auction file (default)."),
                @CommentValue("See security section in config.yml and blocked-materials in auctions/global.yml."),
        })
        public boolean materialWhitelistMode = false;
        @Comment({
                @CommentValue("UUIDs blocked from selling (config); admins can extend at runtime"),
                @CommentValue("Player names are not accepted — use UUID (mcuuid.io, server logs, etc.)"),
                @CommentValue("Example (block seller b0bob0):"),
                @CommentValue("  player-blacklist:"),
                @CommentValue("    - \"xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx\""),
                @CommentValue("Or in game: /ah admin blacklist add b0bob0"),
        })
        public List<String> playerBlacklist = List.of();
    }

    public static final class AnnouncementSettings {

        @Comment({@CommentValue("Item auctions (/ah): purchase and listing broadcasts.")})
        public ItemAnnouncementSettings items = new ItemAnnouncementSettings();
        @Comment({@CommentValue("Region market (/ah regions): purchase and listing broadcasts.")})
        public RegionAnnouncementSettings regions = new RegionAnnouncementSettings();
    }

    public static final class ItemAnnouncementSettings {

        @Comment({@CommentValue("Broadcast when a player buys an item listing")})
        public boolean broadcastPurchase = true;
        @Comment({@CommentValue("Broadcast when a player lists an item on the auction")})
        public boolean broadcastListing = false;
        @Comment({@CommentValue("Minimum purchase price to broadcast, 0 = all purchases")})
        public int minPurchasePrice = 5000;
        @Comment({@CommentValue("Minimum listing price to broadcast, 0 = all listings")})
        public int minListingPrice = 0;
    }

    public static final class RegionAnnouncementSettings {

        @Comment({@CommentValue("Broadcast when a player buys a WorldGuard region on the market")})
        public boolean broadcastPurchase = true;
        @Comment({@CommentValue("Broadcast when a player lists a WorldGuard region for sale")})
        public boolean broadcastListing = false;
        @Comment({@CommentValue("Minimum purchase price to broadcast, 0 = all purchases")})
        public int minPurchasePrice = 5000;
        @Comment({@CommentValue("Minimum listing price to broadcast, 0 = all listings")})
        public int minListingPrice = 0;
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

    public static final class FakeActivityRootSettings {

        @Comment({
                @CommentValue("Subfolder: settings.yml, sellers.yml, items.yml"),
        })
        public String directory = "fake-activity";
    }

    public static final class RegionMarketSettings {

        @Comment({
                @CommentValue("Enable /ah regions (requires WorldGuard on the server)."),
                @CommentValue("Default true — set false to disable on servers without region market."),
        })
        public boolean enabled = true;

        @Comment({
                @CommentValue("Data subfolder under plugins/SoulAuction/ (created when module activates)."),
        })
        public String directory = "regions";

        @Comment({
                @CommentValue("Auction ids allowed for region sales (economy per auction)."),
                @CommentValue("Empty = every auction with sell-enabled in auctions/*.yml."),
        })
        public List<String> allowedAuctionIds = List.of();

        @Comment({@CommentValue("Icon material for region listings in GUI")})
        public String listIconMaterial = "MAP";

        @Comment({@CommentValue("GUI slot for «Sell region» button (0–53)")})
        public int sellButtonSlot = 47;

        @Comment({@CommentValue("Max active region listings per player, 0 = use global listing limits")})
        public int maxListingsPerPlayer = 0;

        @Comment({
                @CommentValue("Hide Bukkit world name in region market UI and sell input."),
                @CommentValue("Players sell with region id only (/ah regions sell shop global 5000)."),
                @CommentValue("world:region still works when false or for duplicate ids across worlds."),
        })
        public boolean hideWorldName = true;

        @Comment({@CommentValue("RMB on a region listing teleports the viewer for preview (safe spot inside region).")})
        public boolean previewTeleportEnabled = true;

        @Comment({@CommentValue("Cooldown between preview teleports per player, seconds. 0 = none.")})
        public int previewTeleportCooldownSeconds = 15;

        @Comment({@CommentValue("Preview session length in seconds. 0 = until cancel or forced exit.")})
        public int previewDurationSeconds = 20;

        @Comment({
                @CommentValue("Spectator mode during preview (GM 3) — no grief, fly through blocks."),
                @CommentValue("false = adventure + flight inside the region."),
        })
        public boolean previewSpectatorMode = true;

        @Comment({@CommentValue("Max length of seller region description (listing lore + owner edit).")})
        public int maxDescriptionLength = 200;

        @Comment({
                @CommentValue("Extra /ah subcommands for region market (besides regions)."),
                @CommentValue("Example: rg → /ah rg sell … (same as /ah regions …)."),
        })
        public List<String> ahSubcommandAliases = List.of("rg");

        @Comment({
                @CommentValue("Top-level commands for region market, e.g. /regions sell …"),
                @CommentValue("Do not add rg here when WorldGuard is installed — it owns /rg."),
                @CommentValue("Use /ah rg instead, or pick a custom name (soulregions, ahregions, …)."),
        })
        public List<String> standaloneCommands = List.of("regions");
    }
}
