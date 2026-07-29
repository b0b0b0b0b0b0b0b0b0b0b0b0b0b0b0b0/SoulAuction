package bm.b0b0b0.soulAuction.model;

import org.bukkit.Material;

public enum AuctionCategory {
    ALL,
    BLOCKS,
    WEAPONS,
    TOOLS,
    ARMOR,
    FOOD,
    REDSTONE,
    OTHER;

    public static AuctionCategory fromMaterial(Material material) {
        String name = material.name();
        if (material.isBlock()) {
            return BLOCKS;
        }
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_BOW") || name.endsWith("_CROSSBOW") || name.endsWith("_TRIDENT")) {
            return WEAPONS;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_SHEARS") || name.endsWith("_FISHING_ROD")) {
            return TOOLS;
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            return ARMOR;
        }
        if (material.isEdible()) {
            return FOOD;
        }
        if (name.contains("REDSTONE") || name.contains("COMPARATOR") || name.contains("REPEATER") || name.contains("OBSERVER")) {
            return REDSTONE;
        }
        return OTHER;
    }
}
