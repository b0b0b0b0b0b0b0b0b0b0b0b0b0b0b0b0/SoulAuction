package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemSettings;
import java.util.ArrayList;
import java.util.List;

public final class FakeActivityDefaults {

    private record DefaultItem(String id, String material, int amount, int minPrice, int maxPrice, int weight) {
    }

    private static final List<DefaultItem> ITEMS = List.of(
            new DefaultItem("diamond", "DIAMOND", 1, 500, 5000, 3),
            new DefaultItem("emerald-stack", "EMERALD", 16, 200, 2500, 4),
            new DefaultItem("iron-ingot-stack", "IRON_INGOT", 32, 150, 1800, 5),
            new DefaultItem("gold-ingot-stack", "GOLD_INGOT", 16, 200, 2200, 4),
            new DefaultItem("copper-ingot-stack", "COPPER_INGOT", 32, 120, 1400, 5),
            new DefaultItem("netherite-ingot", "NETHERITE_INGOT", 1, 4000, 32000, 1),
            new DefaultItem("netherite-scrap", "NETHERITE_SCRAP", 1, 1500, 12000, 2),
            new DefaultItem("ancient-debris", "ANCIENT_DEBRIS", 2, 2000, 16000, 2),
            new DefaultItem("coal-stack", "COAL", 64, 70, 650, 4),
            new DefaultItem("charcoal-stack", "CHARCOAL", 64, 65, 620, 4),
            new DefaultItem("lapis-stack", "LAPIS_LAZULI", 32, 120, 1100, 4),
            new DefaultItem("redstone-stack", "REDSTONE", 64, 90, 800, 4),
            new DefaultItem("quartz-stack", "QUARTZ", 32, 160, 1500, 4),
            new DefaultItem("amethyst-shard", "AMETHYST_SHARD", 16, 180, 1700, 4),
            new DefaultItem("echo-shard", "ECHO_SHARD", 4, 700, 6500, 2),
            new DefaultItem("iron-block", "IRON_BLOCK", 4, 450, 4200, 3),
            new DefaultItem("gold-block", "GOLD_BLOCK", 2, 700, 6500, 2),
            new DefaultItem("diamond-block", "DIAMOND_BLOCK", 1, 2500, 20000, 1),
            new DefaultItem("emerald-block", "EMERALD_BLOCK", 2, 900, 8500, 2),
            new DefaultItem("copper-block", "COPPER_BLOCK", 8, 260, 2400, 4),
            new DefaultItem("obsidian", "OBSIDIAN", 16, 350, 3200, 3),
            new DefaultItem("crying-obsidian", "CRYING_OBSIDIAN", 8, 420, 3900, 3),
            new DefaultItem("glowstone", "GLOWSTONE", 32, 180, 1600, 4),
            new DefaultItem("sea-lantern", "SEA_LANTERN", 8, 260, 2400, 3),
            new DefaultItem("end-stone", "END_STONE", 32, 140, 1300, 4),
            new DefaultItem("purpur-block", "PURPUR_BLOCK", 16, 220, 2000, 3),
            new DefaultItem("prismarine", "PRISMARINE", 16, 200, 1900, 4),
            new DefaultItem("dark-prismarine", "DARK_PRISMARINE", 8, 280, 2600, 3),
            new DefaultItem("netherrack-stack", "NETHERRACK", 64, 40, 380, 4),
            new DefaultItem("blackstone-stack", "BLACKSTONE", 32, 90, 850, 4),
            new DefaultItem("basalt-stack", "BASALT", 32, 85, 800, 4),
            new DefaultItem("deepslate-stack", "DEEPSLATE", 64, 55, 520, 4),
            new DefaultItem("cobblestone-stack", "COBBLESTONE", 64, 45, 420, 5),
            new DefaultItem("stone-stack", "STONE", 64, 50, 460, 5),
            new DefaultItem("sand-stack", "SAND", 64, 35, 320, 4),
            new DefaultItem("gravel-stack", "GRAVEL", 64, 40, 360, 4),
            new DefaultItem("glass-stack", "GLASS", 64, 55, 480, 4),
            new DefaultItem("terracotta", "TERRACOTTA", 32, 100, 950, 4),
            new DefaultItem("white-concrete", "WHITE_CONCRETE", 32, 110, 1000, 4),
            new DefaultItem("oak-log-stack", "OAK_LOG", 64, 50, 450, 4),
            new DefaultItem("birch-log-stack", "BIRCH_LOG", 64, 50, 450, 4),
            new DefaultItem("spruce-log-stack", "SPRUCE_LOG", 64, 50, 450, 4),
            new DefaultItem("dark-oak-log-stack", "DARK_OAK_LOG", 64, 55, 480, 4),
            new DefaultItem("cherry-log-stack", "CHERRY_LOG", 64, 60, 520, 4),
            new DefaultItem("bamboo-stack", "BAMBOO", 32, 70, 650, 4),
            new DefaultItem("sugar-cane-stack", "SUGAR_CANE", 32, 65, 600, 4),
            new DefaultItem("wheat-stack", "WHEAT", 64, 45, 420, 4),
            new DefaultItem("carrot-stack", "CARROT", 64, 50, 460, 4),
            new DefaultItem("potato-stack", "POTATO", 64, 48, 440, 4),
            new DefaultItem("beetroot-stack", "BEETROOT", 64, 46, 430, 4),
            new DefaultItem("melon-slice-stack", "MELON_SLICE", 64, 40, 380, 4),
            new DefaultItem("pumpkin", "PUMPKIN", 16, 80, 750, 4),
            new DefaultItem("cooked-beef-stack", "COOKED_BEEF", 64, 80, 600, 4),
            new DefaultItem("cooked-pork-stack", "COOKED_PORKCHOP", 64, 75, 580, 4),
            new DefaultItem("cooked-chicken-stack", "COOKED_CHICKEN", 64, 70, 560, 4),
            new DefaultItem("bread-stack", "BREAD", 32, 55, 500, 4),
            new DefaultItem("golden-carrot-stack", "GOLDEN_CARROT", 16, 220, 2000, 3),
            new DefaultItem("golden-apple", "GOLDEN_APPLE", 8, 400, 3800, 3),
            new DefaultItem("enchanted-golden-apple", "ENCHANTED_GOLDEN_APPLE", 1, 2000, 15000, 2),
            new DefaultItem("honey-bottle", "HONEY_BOTTLE", 8, 180, 1600, 3),
            new DefaultItem("honeycomb", "HONEYCOMB", 16, 160, 1500, 4),
            new DefaultItem("cod", "COD", 16, 60, 550, 4),
            new DefaultItem("salmon", "SALMON", 16, 65, 580, 4),
            new DefaultItem("cooked-cod", "COOKED_COD", 16, 90, 820, 4),
            new DefaultItem("cooked-salmon", "COOKED_SALMON", 16, 95, 860, 4),
            new DefaultItem("iron-sword", "IRON_SWORD", 1, 120, 1500, 4),
            new DefaultItem("diamond-sword", "DIAMOND_SWORD", 1, 900, 8000, 3),
            new DefaultItem("netherite-sword", "NETHERITE_SWORD", 1, 5000, 38000, 1),
            new DefaultItem("iron-pickaxe", "IRON_PICKAXE", 1, 150, 1700, 4),
            new DefaultItem("diamond-pickaxe", "DIAMOND_PICKAXE", 1, 800, 7000, 3),
            new DefaultItem("diamond-axe", "DIAMOND_AXE", 1, 750, 6800, 3),
            new DefaultItem("bow", "BOW", 1, 100, 1200, 4),
            new DefaultItem("crossbow", "CROSSBOW", 1, 180, 1700, 3),
            new DefaultItem("mace", "MACE", 1, 4500, 35000, 1),
            new DefaultItem("shield", "SHIELD", 1, 140, 1300, 4),
            new DefaultItem("trident", "TRIDENT", 1, 3500, 28000, 1),
            new DefaultItem("arrow-stack", "ARROW", 64, 60, 500, 4),
            new DefaultItem("wind-charge", "WIND_CHARGE", 8, 350, 3200, 3),
            new DefaultItem("iron-helmet", "IRON_HELMET", 1, 180, 1700, 3),
            new DefaultItem("diamond-chestplate", "DIAMOND_CHESTPLATE", 1, 1200, 11000, 2),
            new DefaultItem("netherite-boots", "NETHERITE_BOOTS", 1, 3800, 30000, 1),
            new DefaultItem("tnt", "TNT", 8, 500, 4500, 3),
            new DefaultItem("ender-pearl", "ENDER_PEARL", 4, 250, 2800, 4),
            new DefaultItem("ender-chest", "ENDER_CHEST", 1, 600, 5500, 2),
            new DefaultItem("blaze-rod", "BLAZE_ROD", 8, 300, 3500, 3),
            new DefaultItem("blaze-powder", "BLAZE_POWDER", 16, 280, 2600, 3),
            new DefaultItem("ghast-tear", "GHAST_TEAR", 4, 600, 5500, 2),
            new DefaultItem("magma-cream", "MAGMA_CREAM", 8, 320, 3000, 3),
            new DefaultItem("slime-ball", "SLIME_BALL", 16, 220, 2100, 4),
            new DefaultItem("bone-stack", "BONE", 32, 70, 650, 4),
            new DefaultItem("string-stack", "STRING", 32, 65, 600, 4),
            new DefaultItem("gunpowder-stack", "GUNPOWDER", 16, 180, 1700, 4),
            new DefaultItem("spider-eye", "SPIDER_EYE", 8, 140, 1300, 4),
            new DefaultItem("leather-stack", "LEATHER", 16, 90, 850, 4),
            new DefaultItem("feather-stack", "FEATHER", 32, 55, 500, 4),
            new DefaultItem("phantom-membrane", "PHANTOM_MEMBRANE", 4, 500, 4600, 3),
            new DefaultItem("experience-bottle", "EXPERIENCE_BOTTLE", 16, 400, 4000, 3),
            new DefaultItem("book-stack", "BOOK", 16, 120, 1100, 4),
            new DefaultItem("bookshelf", "BOOKSHELF", 16, 200, 1800, 3),
            new DefaultItem("name-tag", "NAME_TAG", 2, 300, 2800, 3),
            new DefaultItem("saddle", "SADDLE", 1, 350, 3200, 3),
            new DefaultItem("lead", "LEAD", 2, 180, 1700, 4),
            new DefaultItem("heart-of-the-sea", "HEART_OF_THE_SEA", 1, 1200, 10000, 2),
            new DefaultItem("nautilus-shell", "NAUTILUS_SHELL", 2, 700, 6500, 2),
            new DefaultItem("conduit", "CONDUIT", 1, 2500, 22000, 1),
            new DefaultItem("sponge", "SPONGE", 4, 800, 7500, 2),
            new DefaultItem("shulker-box", "SHULKER_BOX", 1, 900, 8000, 2),
            new DefaultItem("white-shulker-box", "WHITE_SHULKER_BOX", 1, 950, 8500, 2),
            new DefaultItem("elytra", "ELYTRA", 1, 8000, 45000, 1),
            new DefaultItem("totem", "TOTEM_OF_UNDYING", 1, 6000, 40000, 1),
            new DefaultItem("nether-star", "NETHER_STAR", 1, 5000, 35000, 1),
            new DefaultItem("beacon", "BEACON", 1, 7000, 50000, 1),
            new DefaultItem("dragon-breath", "DRAGON_BREATH", 4, 900, 8500, 2),
            new DefaultItem("breeze-rod", "BREEZE_ROD", 4, 650, 6000, 2),
            new DefaultItem("trial-key", "TRIAL_KEY", 2, 800, 7500, 2),
            new DefaultItem("ominous-trial-key", "OMINOUS_TRIAL_KEY", 1, 1500, 14000, 1),
            new DefaultItem("heavy-core", "HEAVY_CORE", 1, 2200, 20000, 1),
            new DefaultItem("netherite-upgrade", "NETHERITE_UPGRADE_SMITHING_TEMPLATE", 1, 1800, 16000, 2),
            new DefaultItem("diamond-horse-armor", "DIAMOND_HORSE_ARMOR", 1, 900, 8500, 2)
    );

    private static final List<String> SELLER_NAMES = List.of(
            "shadowstone", "frostflame", "ironvein", "mistwalker", "runestone", "swiftclaw", "ashenpaw", "hollowgrove",
            "mooncross", "dawnvelvet", "stormpetal", "cinderpine", "mossglow", "ravensilk", "ivorymoth", "frostpine",
            "NetherRook", "VoidMason", "QuartzClutch", "WardenScout", "EmberBuilder", "CaveScout", "TaigaKeeper",
            "BlockArtisan", "OakMason", "CopperRoof", "RedstoneRook", "CastleCrafter", "DiamondSeeker", "ObsidianKnight",
            "EnderExplorer", "FrostByte", "VoidBlade", "PixelProwler", "NetherBlade", "LavaLurker", "GlowstoneGuru",
            "DiamondDigger", "MagmaMaverick", "PickaxeProwler", "RedstoneRanger", "BlockyBuilder", "TntTwister",
            "PrismWalker", "CobbleShade", "LapisFury", "ForgeWarden", "BedrockTitan", "ShardStorm", "RuneFlare",
            "StoneViper", "BlazeSentinel", "LunarCrafter", "OceanShifter", "SkyForge", "MysticVigil", "TorchBearer",
            "IronInnovator", "GoldGatherer", "QuartzQuest", "VortexBlaze", "CrystalSpike", "IceHammer", "SkyPhantom",
            "diamond_miner", "creeper_avert", "lava_adjacent", "dirtarchitect", "woodpuncher", "nether_wolf", "ender_scout",
            "redstone_owl", "cave_dweller", "slime_hunter", "villager_fan", "anvil_tapper", "hopper_king", "sculk_echo",
            "trial_runner", "mace_swing", "wind_charge", "deep_dark", "ancient_city", "piglin_barter", "bastion_loot",
            "chorus_fruit", "shulker_box", "phantom_mem", "blaze_rod_x", "wither_rose", "soul_sand", "deepslate",
            "amethyst_geode", "copper_golem", "iron_golem", "snow_golem", "turtle_helm", "axolotl_jar", "goat_horn",
            "echo_shard", "heavy_core", "ominous_key", "stack_overflow", "slot_nine", "chunk_loader", "spawn_runner",
            "loot_goblin", "trade_pilot", "bid_snatcher", "lot_flipper", "market_moth", "auction_fox", "sell_queue"
    );

    private FakeActivityDefaults() {
    }

    public static List<String> defaultSellerNames() {
        return SELLER_NAMES;
    }

    public static List<FakeActivityItemSettings> defaultItems() {
        List<FakeActivityItemSettings> items = new ArrayList<>(ITEMS.size());
        for (DefaultItem item : ITEMS) {
            items.add(toSettings(item));
        }
        return items;
    }

    private static FakeActivityItemSettings toSettings(DefaultItem item) {
        FakeActivityItemSettings settings = new FakeActivityItemSettings();
        settings.id = item.id;
        settings.material = item.material;
        settings.amount = item.amount;
        settings.minPrice = item.minPrice;
        settings.maxPrice = item.maxPrice;
        settings.weight = item.weight;
        return settings;
    }
}
