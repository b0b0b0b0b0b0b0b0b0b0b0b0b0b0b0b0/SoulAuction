package bm.b0b0b0.soulAuction.repository;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.model.StorageMode;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionRepositoryFactory {

    private AuctionRepositoryFactory() {
    }

    public static AuctionRepository create(JavaPlugin plugin, AuctionSettings settings) {
        StorageMode mode = StorageMode.fromString(settings.storage.mode);
        Path dataFolder = plugin.getDataFolder().toPath();
        Path flatDirectory = dataFolder.resolve(settings.storage.flatDirectory);
        return switch (mode) {
            case YAML -> new YamlPerItemRepository(flatDirectory);
            case SQLITE, MYSQL -> new SqlAuctionRepository(mode, settings.storage.database, dataFolder);
            case JSON -> new JsonPerItemRepository(flatDirectory);
        };
    }
}
