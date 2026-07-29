package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.GuiGeneralSettings;
import java.util.List;

public record PluginConfig(
        AuctionSettings auctionSettings,
        GuiGeneralSettings guiGeneralSettings,
        List<AuctionDefinitionSettings> auctionDefinitions
) {
}
