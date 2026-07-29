package bm.b0b0b0.soulAuction.gui;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class ContainerPreviewMenu implements InventoryHolder {

    private final UUID viewerId;
    private final String returnAuctionId;
    private final Inventory inventory;

    public ContainerPreviewMenu(UUID viewerId, String returnAuctionId, int size, net.kyori.adventure.text.Component title) {
        this.viewerId = viewerId;
        this.returnAuctionId = returnAuctionId;
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public String returnAuctionId() {
        return returnAuctionId;
    }

    public void fillFrom(Container container) {
        ItemStack[] contents = container.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, inventory.getSize()); i++) {
            if (contents[i] != null) {
                inventory.setItem(i, contents[i].clone());
            }
        }
    }
}
