package bm.b0b0b0.soulAuction;

import bm.b0b0b0.soulAuction.command.AuctionCommand;
import bm.b0b0b0.soulAuction.command.AuctionAliasListener;
import bm.b0b0b0.soulAuction.config.ConfigurationLoader;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.gui.AuctionGuiListener;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.listener.AuctionSearchChatListener;
import bm.b0b0b0.soulAuction.listener.PlayerSaleNotificationListener;
import bm.b0b0b0.soulAuction.service.AuctionListingCache;
import bm.b0b0b0.soulAuction.service.CoinsEngineBridge;
import bm.b0b0b0.soulAuction.service.ExperienceEconomyBridge;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.repository.AuctionRepositoryFactory;
import bm.b0b0b0.soulAuction.model.StorageMode;
import bm.b0b0b0.soulAuction.placeholder.SoulAuctionPlaceholderExpansion;
import bm.b0b0b0.soulAuction.service.AuctionExternalNotifier;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.EconomyBridge;
import bm.b0b0b0.soulAuction.service.PermissionLimitResolver;
import bm.b0b0b0.soulAuction.service.PlayerPointsBridge;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.PriceLimitResolver;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import bm.b0b0b0.soulAuction.service.TaxPolicyResolver;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulAuction extends JavaPlugin {

    private ConfigurationLoader configurationLoader;
    private PluginConfig pluginConfig;
    private MessageService messageService;
    private AuctionService auctionService;
    private AuctionRepository repository;
    private RedisSellGuard redisSellGuard;
    private AuctionRuntimeStorage runtimeStorage;

    @Override
    public void onEnable() {
        configurationLoader = new ConfigurationLoader(this);
        pluginConfig = configurationLoader.load();
        messageService = new MessageService(this);
        repository = AuctionRepositoryFactory.create(this, pluginConfig.auctionSettings());
        runtimeStorage = new AuctionRuntimeStorage(getDataFolder().toPath());
        EconomyBridge economyBridge = new EconomyBridge(this);
        PlayerPointsBridge playerPointsBridge = new PlayerPointsBridge(this);
        StorageMode storageMode = StorageMode.fromString(pluginConfig.auctionSettings().storage.mode);
        boolean useRedisSellGuard = storageMode == StorageMode.MYSQL;
        redisSellGuard = new RedisSellGuard(useRedisSellGuard, pluginConfig.auctionSettings().storage.redis);
        PermissionLimitResolver permissionLimitResolver = new PermissionLimitResolver(getName());
        PermissionPriorityResolver priorityResolver = new PermissionPriorityResolver();
        TaxPolicyResolver taxPolicyResolver = new TaxPolicyResolver();
        PriceLimitResolver priceLimitResolver = new PriceLimitResolver();
        ExperienceEconomyBridge experienceEconomyBridge = new ExperienceEconomyBridge();
        CoinsEngineBridge coinsEngineBridge = new CoinsEngineBridge(this, pluginConfig.auctionSettings().coinsEngineCurrency);
        AuctionListingCache listingCache = new AuctionListingCache();
        AuctionExternalNotifier externalNotifier = new AuctionExternalNotifier(this, () -> pluginConfig.auctionSettings());
        auctionService = new AuctionService(
                repository,
                this::pluginConfig,
                economyBridge,
                playerPointsBridge,
                experienceEconomyBridge,
                coinsEngineBridge,
                permissionLimitResolver,
                priorityResolver,
                redisSellGuard,
                runtimeStorage,
                taxPolicyResolver,
                priceLimitResolver,
                externalNotifier,
                messageService,
                listingCache
        );
        auctionService.attachCacheSubscriber();
        getLogger().info("Vault connected: " + auctionService.hasVault());
        getLogger().info("PlayerPoints connected: " + auctionService.hasPlayerPoints());
        try {
            auctionService.load().join();
            getLogger().info("Auctions loaded");
        } catch (Exception exception) {
            getLogger().severe("Cannot load auctions: " + exception.getMessage());
        }
        getServer().getPluginManager().registerEvents(new AuctionGuiListener(this, this::pluginConfig, auctionService, messageService), this);
        getServer().getPluginManager().registerEvents(new PlayerSaleNotificationListener(auctionService, messageService), this);
        getServer().getPluginManager().registerEvents(new AuctionAliasListener(this::pluginConfig), this);
        getServer().getPluginManager().registerEvents(
                new AuctionSearchChatListener(this, this::pluginConfig, auctionService, messageService),
                this
        );
        getCommand("ah").setExecutor(new AuctionCommand(
                this,
                this::pluginConfig,
                messageService,
                auctionService,
                this::reloadAll
        ));
        PluginSchedulers.runAsyncTimer(
                this,
                100L,
                auctionService.expireCheckIntervalSeconds() * 20L,
                () -> {
                    int expired = auctionService.expireListings();
                    if (expired > 0) {
                        getLogger().info("Expired listings: " + expired);
                    }
                }
        );
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SoulAuctionPlaceholderExpansion(auctionService).register();
            getLogger().info("PlaceholderAPI expansion registered");
        }
    }

    @Override
    public void onDisable() {
        if (auctionService == null) {
            return;
        }
        try {
            auctionService.close().join();
        } catch (Exception exception) {
            getLogger().severe("Cannot save auctions: " + exception.getMessage());
        }
        if (redisSellGuard != null) {
            redisSellGuard.close();
        }
    }

    private PluginConfig pluginConfig() {
        return pluginConfig;
    }

    private void reloadAll() {
        pluginConfig = configurationLoader.load();
        messageService.reload();
    }
}
