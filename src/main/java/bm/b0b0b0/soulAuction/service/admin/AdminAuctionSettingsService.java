package bm.b0b0b0.soulAuction.service.admin;

import bm.b0b0b0.soulAuction.config.AuctionDefinitionWriter;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.settings.AuctionDefinitionSettings;
import bm.b0b0b0.soulAuction.config.settings.AuctionSettings;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminAuctionSettingsService {

    private final JavaPlugin plugin;
    private final Supplier<PluginConfig> configSupplier;
    private final AuctionDefinitionWriter definitionWriter;
    private final Runnable reloadConfig;
    private final AuctionService auctionService;
    private final MessageService messageService;
    private final ConcurrentHashMap<UUID, Boolean> toggleInFlight = new ConcurrentHashMap<>();

    public AdminAuctionSettingsService(
            JavaPlugin plugin,
            Supplier<PluginConfig> configSupplier,
            AuctionDefinitionWriter definitionWriter,
            Runnable reloadConfig,
            AuctionService auctionService,
            MessageService messageService
    ) {
        this.plugin = plugin;
        this.configSupplier = configSupplier;
        this.definitionWriter = definitionWriter;
        this.reloadConfig = reloadConfig;
        this.auctionService = auctionService;
        this.messageService = messageService;
    }

    public void toggleFakeActivity(Player player, String auctionId, Runnable onSuccess) {
        if (toggleInFlight.putIfAbsent(player.getUniqueId(), Boolean.TRUE) != null) {
            return;
        }
        AuctionDefinitionSettings definition = auctionService.findAuctionDefinition(auctionId);
        if (definition == null) {
            toggleInFlight.remove(player.getUniqueId());
            messageService.send(player, "error-admin-settings-auction-missing");
            return;
        }
        boolean enabled = !definition.fakeActivityEnabled;
        definition.fakeActivityEnabled = enabled;
        AuctionSettings auctionSettings = configSupplier.get().auctionSettings();
        PluginSchedulers.runAsync(plugin, () -> {
            boolean saved = false;
            try {
                definitionWriter.saveDefinition(auctionSettings, definition);
                saved = true;
            } catch (Exception ignored) {
            }
            boolean finalSaved = saved;
            PluginSchedulers.runGlobal(plugin, () -> {
                if (finalSaved) {
                    reloadConfig.run();
                }
                PluginSchedulers.run(plugin, player, () -> {
                    toggleInFlight.remove(player.getUniqueId());
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!finalSaved) {
                        definition.fakeActivityEnabled = !enabled;
                        messageService.send(player, "error-admin-settings-save-failed");
                    } else {
                        String key = enabled
                                ? "success-admin-settings-fake-enabled"
                                : "success-admin-settings-fake-disabled";
                        messageService.send(player, key, Map.of("auction", definition.id));
                        auctionService.audit(
                                player.getUniqueId(),
                                player.getName(),
                                "ADMIN_TOGGLE_FAKE",
                                "auction=" + definition.id + ",enabled=" + enabled
                        );
                    }
                    if (onSuccess != null) {
                        onSuccess.run();
                    }
                });
            });
        });
    }
}
