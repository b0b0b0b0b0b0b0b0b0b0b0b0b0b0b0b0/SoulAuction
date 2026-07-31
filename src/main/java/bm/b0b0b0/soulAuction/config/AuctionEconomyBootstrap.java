package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.service.EconomyIntegrationProbe;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuctionEconomyBootstrap {

    private AuctionEconomyBootstrap() {
    }

    public static List<String> alignDefinitions(JavaPlugin plugin, Path auctionsDirectory, Iterable<AuctionDefinitionSettings> definitions) {
        List<String> changes = new ArrayList<>();
        for (AuctionDefinitionSettings definition : definitions) {
            if (EconomyIntegrationProbe.isEconomyReady(plugin, definition.economy)) {
                continue;
            }
            String previous = definition.economy == null ? "VAULT" : definition.economy;
            EconomyIntegrationProbe.applyPreferredEconomy(plugin, definition);
            Path file = auctionsDirectory.resolve(definition.id + ".yml");
            if (Files.exists(file)) {
                definition.save(file);
            }
            changes.add("Auction '" + definition.id + "' economy: " + previous + " -> " + definition.economy);
        }
        return changes;
    }
}
