package bm.b0b0b0.soulAuction;

import bm.b0b0b0.soulAuction.bootstrap.SoulAuctionStartupLog;
import bm.b0b0b0.soulAuction.command.AuctionCommand;
import bm.b0b0b0.soulAuction.command.AuctionAliasListener;
import bm.b0b0b0.soulAuction.config.AuctionDefinitionWriter;
import bm.b0b0b0.soulAuction.config.ConfigurationLoader;
import bm.b0b0b0.soulAuction.config.PluginConfig;
import bm.b0b0b0.soulAuction.config.StorageRuntimeMeta;
import bm.b0b0b0.soulAuction.gui.AuctionGuiListener;
import bm.b0b0b0.soulAuction.lang.MessageService;
import bm.b0b0b0.soulAuction.listener.AdminAuctionCreateChatListener;
import bm.b0b0b0.soulAuction.listener.AuctionSearchChatListener;
import bm.b0b0b0.soulAuction.listener.PlayerSaleNotificationListener;
import bm.b0b0b0.soulAuction.model.StorageMode;
import bm.b0b0b0.soulAuction.service.migration.AuctionStorageMigrator;
import bm.b0b0b0.soulAuction.placeholder.SoulAuctionPlaceholderExpansion;
import bm.b0b0b0.soulAuction.repository.AuctionRepository;
import bm.b0b0b0.soulAuction.repository.AuctionRepositoryFactory;
import bm.b0b0b0.soulAuction.service.AuctionExternalNotifier;
import bm.b0b0b0.soulAuction.service.AuctionListingCache;
import bm.b0b0b0.soulAuction.service.AuctionRuntimeStorage;
import bm.b0b0b0.soulAuction.service.AuctionService;
import bm.b0b0b0.soulAuction.service.admin.AdminAuctionCreateService;
import bm.b0b0b0.soulAuction.service.CoinsEngineBridge;
import bm.b0b0b0.soulAuction.service.EconomyBridge;
import bm.b0b0b0.soulAuction.service.ExperienceEconomyBridge;
import bm.b0b0b0.soulAuction.service.PermissionLimitResolver;
import bm.b0b0b0.soulAuction.service.PermissionPriorityResolver;
import bm.b0b0b0.soulAuction.service.PlayerPointsBridge;
import bm.b0b0b0.soulAuction.service.PriceLimitResolver;
import bm.b0b0b0.soulAuction.service.RedisSellGuard;
import bm.b0b0b0.soulAuction.service.TaxPolicyResolver;
import bm.b0b0b0.soulAuction.util.PluginSchedulers;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulAuction extends JavaPlugin {

    private SoulAuctionStartupLog startupLog;
    private ConfigurationLoader configurationLoader;
    private PluginConfig pluginConfig;
    private MessageService messageService;
    private AuctionService auctionService;
    private AdminAuctionCreateService adminAuctionCreateService;
    private AuctionRepository repository;
    private RedisSellGuard redisSellGuard;
    private AuctionRuntimeStorage runtimeStorage;

    @Override
    public void onEnable() {
        startupLog = new SoulAuctionStartupLog();
        startupLog.bannerStart(getPluginMeta().getVersion());
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
                startupLog.stepFail("Папка данных — не удалось создать");
            }
            startupLog.info("Загрузка конфигурации...");
            configurationLoader = new ConfigurationLoader(this);
            pluginConfig = configurationLoader.load();
            startupLog.stepSchedulers();
            StorageMode storageMode = StorageMode.fromString(pluginConfig.auctionSettings().storage.mode);
            startupLog.stepOk("Конфиг — storage=" + storageMode.name()
                    + ", аукционов=" + pluginConfig.auctionDefinitions().size());
            logStorageConfigChange();
            messageService = new MessageService(this);
            wireMessageServiceConfig();
            startupLog.stepOk("Сообщения — lang: " + String.join(", ", messageService.loadedLocaleIds()));
            repository = AuctionRepositoryFactory.create(this, pluginConfig.auctionSettings());
            runtimeStorage = new AuctionRuntimeStorage(getDataFolder().toPath());
            EconomyBridge economyBridge = new EconomyBridge(this);
            PlayerPointsBridge playerPointsBridge = new PlayerPointsBridge(this);
            boolean useRedisSellGuard = storageMode == StorageMode.MYSQL;
            redisSellGuard = new RedisSellGuard(this, useRedisSellGuard, pluginConfig.auctionSettings().storage.redis);
            logRedis(useRedisSellGuard);
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
            logIntegrations();
            AuctionDefinitionWriter definitionWriter = new AuctionDefinitionWriter(this);
            adminAuctionCreateService = new AdminAuctionCreateService(
                    this::pluginConfig,
                    definitionWriter,
                    this::reloadAll,
                    auctionService,
                    messageService
            );
            getServer().getPluginManager().registerEvents(
                    new AuctionGuiListener(
                            this,
                            this::pluginConfig,
                            auctionService,
                            messageService,
                            adminAuctionCreateService
                    ),
                    this
            );
            getServer().getPluginManager().registerEvents(new PlayerSaleNotificationListener(auctionService, messageService), this);
            getServer().getPluginManager().registerEvents(new AuctionAliasListener(this::pluginConfig), this);
            getServer().getPluginManager().registerEvents(
                    new AdminAuctionCreateChatListener(
                            this,
                            this::pluginConfig,
                            adminAuctionCreateService,
                            auctionService,
                            messageService
                    ),
                    this
            );
            getServer().getPluginManager().registerEvents(
                    new AuctionSearchChatListener(
                            this,
                            this::pluginConfig,
                            auctionService,
                            messageService,
                            adminAuctionCreateService
                    ),
                    this
            );
            getCommand("ah").setExecutor(new AuctionCommand(
                    this,
                    this::pluginConfig,
                    messageService,
                    auctionService,
                    this::reloadAll,
                    adminAuctionCreateService
            ));
            PluginSchedulers.runGlobalTimer(
                    this,
                    100L,
                    auctionService.expireCheckIntervalSeconds() * 20L,
                    () -> auctionService.expireListings()
            );
            startupLog.info("Загрузка лотов...");
            auctionService.load().whenComplete((unused, exception) -> PluginSchedulers.runGlobal(this, () -> finishStartup(exception)));
        } catch (Throwable throwable) {
            getLogger().severe("SoulAuction enable failed");
            throwable.printStackTrace();
            startupLog.stepFail("Ошибка при старте — см. stack trace выше");
            startupLog.bannerFailure(throwable.getMessage() == null ? "unknown error" : throwable.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void finishStartup(Throwable exception) {
        if (exception != null) {
            getLogger().severe("Cannot load auctions: " + exception.getMessage());
            exception.printStackTrace();
            startupLog.stepFail("Хранилище — ошибка загрузки");
            startupLog.bannerFailure("load failed");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        int listings = auctionService.totalListingsCount();
        startupLog.stepOk("Лоты — активных " + listings);
        StorageMode activeStorage = StorageMode.fromString(pluginConfig.auctionSettings().storage.mode);
        if (listings == 0) {
            StorageMode legacy = AuctionStorageMigrator.detectLegacyStorage(
                    getDataFolder().toPath(),
                    pluginConfig.auctionSettings(),
                    activeStorage
            );
            if (legacy != null) {
                startupLog.stepSkipped("Хранилище пусто — найдены данные " + legacy + "; /ah admin migrate from " + legacy);
            }
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new SoulAuctionPlaceholderExpansion(auctionService, messageService).register();
            startupLog.stepOk("PlaceholderAPI — expansion зарегистрирован");
        } else {
            startupLog.stepSkipped("PlaceholderAPI — не найден");
        }
        startupLog.bannerSuccess();
        StorageRuntimeMeta.write(getDataFolder().toPath(), pluginConfig.auctionSettings());
    }

    private void logStorageConfigChange() {
        StorageRuntimeMeta.ChangeCheck change = StorageRuntimeMeta.compare(
                getDataFolder().toPath(),
                pluginConfig.auctionSettings()
        );
        if (!change.configChanged() || change.previous() == null) {
            return;
        }
        String message = "Storage изменён с прошлого запуска: было "
                + change.previous().mode()
                + ", в config сейчас "
                + change.current().mode()
                + " — проверь лоты и /ah admin migrate при необходимости";
        startupLog.stepFail(message);
        getLogger().warning(message);
    }

    private void logRedis(boolean useRedisSellGuard) {
        if (!useRedisSellGuard) {
            startupLog.stepSkipped("Redis — не используется (storage ≠ MYSQL)");
            return;
        }
        var redis = pluginConfig.auctionSettings().storage.redis;
        if (!redis.enabled) {
            startupLog.stepSkipped("Redis — отключён в конфиге");
            return;
        }
        if (redisSellGuard.enabled()) {
            startupLog.stepOk("Redis — " + redis.host + ":" + redis.port
                    + (redis.pubSubEnabled ? " · pub/sub" : ""));
            return;
        }
        startupLog.stepFail("Redis — не подключён");
    }

    private void logIntegrations() {
        startupLog.info("Интеграции:");
        if (auctionService.hasVault()) {
            startupLog.stepOk("Vault — economy");
        } else if (getServer().getPluginManager().getPlugin("Vault") != null) {
            startupLog.stepFail("Vault — economy не подключена");
        } else {
            startupLog.stepSkipped("Vault — не найден");
        }
        if (auctionService.hasPlayerPoints()) {
            startupLog.stepOk("PlayerPoints — API");
        } else if (getServer().getPluginManager().getPlugin("PlayerPoints") != null) {
            startupLog.stepFail("PlayerPoints — API не готов");
        } else {
            startupLog.stepSkipped("PlayerPoints — не найден");
        }
        if (auctionService.hasCoinsEngine()) {
            startupLog.stepOk("CoinsEngine — подключён");
        } else if (getServer().getPluginManager().getPlugin("CoinsEngine") != null) {
            startupLog.stepSkipped("CoinsEngine — на сервере, валюта не используется");
        } else {
            startupLog.stepSkipped("CoinsEngine — не найден");
        }
    }

    @Override
    public void onDisable() {
        if (auctionService == null) {
            return;
        }
        try {
            auctionService.close().get(30L, TimeUnit.SECONDS);
        } catch (Exception exception) {
            getLogger().severe("Cannot save auctions: " + exception.getMessage());
        }
        if (redisSellGuard != null) {
            redisSellGuard.close();
        }
        if (pluginConfig != null) {
            StorageRuntimeMeta.write(getDataFolder().toPath(), pluginConfig.auctionSettings());
        }
        if (startupLog != null) {
            startupLog.unload();
        }
    }

    private PluginConfig pluginConfig() {
        return pluginConfig;
    }

    private void reloadAll() {
        pluginConfig = configurationLoader.load();
        wireMessageServiceConfig();
        messageService.reload();
    }

    private void wireMessageServiceConfig() {
        messageService.setRespectDisabledMessages(
                () -> pluginConfig.auctionSettings().features.respectDisabledMessages
        );
        messageService.setForcedLocaleSupplier(() -> {
            var messages = pluginConfig.auctionSettings().messages;
            if (!"SERVER".equalsIgnoreCase(messages.localeMode)) {
                return null;
            }
            return messages.serverLocale;
        });
    }

    public AuctionService auctionService() {
        return auctionService;
    }
}
