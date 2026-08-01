package bm.b0b0b0.soulAuction.gui;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiFillerItem {

    private GuiFillerItem() {
    }

    public static ItemStack create(GuiGeneralSettings guiSettings, AuctionDefinitionSettings auctionDefinition) {
        String materialName = guiSettings.fillerMaterial;
        int customModelData = guiSettings.fillerCustomModelData;
        if (auctionDefinition != null
                && auctionDefinition.guiFillerMaterial != null
                && !auctionDefinition.guiFillerMaterial.isBlank()) {
            materialName = auctionDefinition.guiFillerMaterial;
            if (auctionDefinition.guiFillerCustomModelData >= 0) {
                customModelData = auctionDefinition.guiFillerCustomModelData;
            }
        }
        Material material = Material.matchMaterial(materialName == null ? "" : materialName);
        if (material == null || !material.isItem()) {
            material = Material.BLACK_STAINED_GLASS_PANE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            if (customModelData >= 0) {
                meta.setCustomModelData(customModelData);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
