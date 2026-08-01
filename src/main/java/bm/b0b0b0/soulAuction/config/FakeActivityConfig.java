package bm.b0b0b0.soulAuction.config;

import bm.b0b0b0.soulAuction.config.settings.FakeActivityItemSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivitySellersSettings;
import bm.b0b0b0.soulAuction.config.settings.FakeActivitySettings;
import java.util.List;

public record FakeActivityConfig(
        FakeActivitySettings settings,
        FakeActivitySellersSettings sellers,
        List<FakeActivityItemSettings> items
) {
}
