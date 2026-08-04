package bm.b0b0b0.soulAuction.region;

import bm.b0b0b0.soulAuction.command.RegionMarketCommandHandler;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.event.HandlerList;

public final class RegionMarketLifecycle {

    private final RegionMarketDependencies dependencies;
    private RegionMarketModule module;

    public RegionMarketLifecycle(RegionMarketDependencies dependencies) {
        this.dependencies = dependencies;
    }

    public void sync(PluginConfig config) {
        if (RegionMarketActivation.shouldRun(config)) {
            activate(config);
            return;
        }
        deactivate();
    }

    public void shutdown() {
        deactivate();
    }

    public boolean isActive() {
        return module != null;
    }

    public RegionMarketCommandHandler commandHandler() {
        return module == null ? null : module.commandHandler();
    }

    public RegionMarketModule module() {
        return module;
    }

    private void activate(PluginConfig config) {
        ensureDataDirectory(config);
        if (module != null) {
            return;
        }
        module = RegionMarketModule.create(dependencies);
        dependencies.plugin().getServer().getPluginManager().registerEvents(module.guiListener(), dependencies.plugin());
        dependencies.plugin().getServer().getPluginManager().registerEvents(module.chatListener(), dependencies.plugin());
        dependencies.plugin().getServer().getPluginManager().registerEvents(module.commandInterceptListener(), dependencies.plugin());
        dependencies.plugin().getServer().getPluginManager().registerEvents(module.previewListener(), dependencies.plugin());
    }

    private void deactivate() {
        if (module == null) {
            return;
        }
        module.previewSessionService().endAll();
        HandlerList.unregisterAll(module.guiListener());
        HandlerList.unregisterAll(module.chatListener());
        HandlerList.unregisterAll(module.commandInterceptListener());
        HandlerList.unregisterAll(module.previewListener());
        module = null;
    }

    private void ensureDataDirectory(PluginConfig config) {
        String directory = RegionMarketActivation.dataDirectory(config.auctionSettings().regionMarket);
        Path path = dependencies.plugin().getDataFolder().toPath().resolve(directory);
        if (Files.isDirectory(path)) {
            return;
        }
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            dependencies.plugin().getLogger().warning("Region market data folder failed: " + path + " — " + exception.getMessage());
        }
    }
}
