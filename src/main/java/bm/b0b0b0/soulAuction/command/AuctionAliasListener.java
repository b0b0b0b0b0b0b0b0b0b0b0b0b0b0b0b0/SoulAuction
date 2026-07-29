package bm.b0b0b0.soulAuction.command;

import bm.b0b0b0.soulAuction.config.PluginConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class AuctionAliasListener implements Listener {

    private final Supplier<PluginConfig> configSupplier;

    public AuctionAliasListener(Supplier<PluginConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String original = event.getMessage();
        if (original.length() <= 1 || original.charAt(0) != '/') {
            return;
        }
        String raw = original.substring(1);
        int spaceIndex = raw.indexOf(' ');
        String label = spaceIndex >= 0 ? raw.substring(0, spaceIndex) : raw;
        String lowerLabel = label.toLowerCase(Locale.ROOT);
        if (lowerLabel.equals("ah")) {
            return;
        }
        List<String> aliases = configSupplier.get().auctionSettings().commandAliases;
        for (String alias : aliases) {
            if (alias == null || alias.isBlank()) {
                continue;
            }
            if (!lowerLabel.equals(alias.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String suffix = spaceIndex >= 0 ? raw.substring(spaceIndex) : "";
            event.setMessage("/ah" + suffix);
            return;
        }
    }
}
