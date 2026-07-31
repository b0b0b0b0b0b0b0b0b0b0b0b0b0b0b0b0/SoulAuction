package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionDefinitionWriter {

    private final JavaPlugin plugin;

    public AuctionDefinitionWriter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Path auctionsDirectory(AuctionSettings auctionSettings) {
        return plugin.getDataFolder().toPath().resolve(auctionSettings.auctionsDirectory);
    }

    public boolean definitionFileExists(AuctionSettings auctionSettings, String auctionId) {
        return Files.exists(auctionsDirectory(auctionSettings).resolve(auctionId + ".yml"));
    }

    public void writeNewDefinition(AuctionSettings auctionSettings, String auctionId, String displayName) throws IOException {
        Path directory = auctionsDirectory(auctionSettings);
        Files.createDirectories(directory);
        Path file = directory.resolve(auctionId + ".yml");
        if (Files.exists(file)) {
            throw new IOException("Auction file already exists: " + file.getFileName());
        }
        AuctionDefinitionSettings definition = new AuctionDefinitionSettings();
        definition.id = auctionId;
        definition.displayName = displayName == null ? "" : displayName;
        definition.openPermission = "soulauction.open." + auctionId;
        definition.buyPermission = "soulauction.buy." + auctionId;
        definition.sellPermission = "soulauction.sell." + auctionId;
        definition.save(file);
    }
}
