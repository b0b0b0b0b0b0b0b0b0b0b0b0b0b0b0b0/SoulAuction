package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigurationLoader {

    private final JavaPlugin plugin;
    private final Path dataFolderPath;

    public ConfigurationLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolderPath = plugin.getDataFolder().toPath();
    }

    public PluginConfig load() {
        ensureDirectories();
        AuctionSettings auctionSettings = SerializedConfigReloader.reload(
                new AuctionSettings(),
                dataFolderPath.resolve("config.yml")
        );
        GuiGeneralSettings guiGeneralSettings = SerializedConfigReloader.reload(
                new GuiGeneralSettings(),
                dataFolderPath.resolve("gui").resolve("general.yml")
        );
        List<AuctionDefinitionSettings> auctionDefinitions = loadAuctions(auctionSettings);
        return new PluginConfig(auctionSettings, guiGeneralSettings, auctionDefinitions);
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(dataFolderPath.resolve("gui"));
            Files.createDirectories(dataFolderPath.resolve("data"));
            Files.createDirectories(dataFolderPath.resolve("auctions"));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create plugin directories", exception);
        }
    }

    private List<AuctionDefinitionSettings> loadAuctions(AuctionSettings auctionSettings) {
        Path auctionsDirectory = dataFolderPath.resolve(auctionSettings.auctionsDirectory);
        try {
            Files.createDirectories(auctionsDirectory);
            List<AuctionDefinitionSettings> definitions = new ArrayList<>();
            try (var stream = Files.list(auctionsDirectory)) {
                List<Path> files = stream
                        .filter(path -> path.getFileName().toString().endsWith(".yml"))
                        .toList();
                for (Path file : files) {
                    AuctionDefinitionSettings definition = SerializedConfigReloader.reload(new AuctionDefinitionSettings(), file);
                    definitions.add(definition);
                }
            }
            if (definitions.isEmpty()) {
                AuctionDefinitionSettings defaultAuction = SerializedConfigReloader.reload(
                        new AuctionDefinitionSettings(),
                        auctionsDirectory.resolve("global.yml")
                );
                definitions.add(defaultAuction);
            }
            return definitions;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load auction files", exception);
        }
    }
}
