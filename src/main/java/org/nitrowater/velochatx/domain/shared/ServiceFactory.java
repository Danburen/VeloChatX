package org.nitrowater.velochatx.domain.shared;

import lombok.Getter;
import org.nitrowater.velochatx.domain.broadcast.BroadcastService;
import org.nitrowater.velochatx.domain.broadcast.BroadcastServiceImpl;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.channel.ChannelServiceImpl;
import org.nitrowater.velochatx.domain.chat.ChatService;
import org.nitrowater.velochatx.domain.chat.ChatServiceImpl;
import org.nitrowater.velochatx.domain.player.*;
import org.nitrowater.velochatx.domain.player.infra.PlayerBlockedMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerStatusMapper;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.velochatx.domain.server.ServerServiceImpl;
import org.nitrowater.velochatx.domain.tablist.TabListService;
import org.nitrowater.velochatx.domain.tablist.TabListServiceImpl;
import org.nitrowater.velochatx.mapper.migration.V001__CreatePlayerTables;
import org.nitrowater.velochatx.mapper.migration.V002__CreateIgnoreRejectTables;
import org.nitrowater.waterapi.domain.config.application.ConfigManager;
import org.nitrowater.waterapi.domain.config.application.ConfigService;
import org.nitrowater.waterapi.domain.config.schema.ItemSchema;
import org.nitrowater.waterapi.domain.config.schema.KeyItemSchema;
import org.nitrowater.waterapi.domain.infra.io.database.Database;
import org.nitrowater.waterapi.domain.infra.io.database.H2DataBase;
import org.nitrowater.waterapi.domain.infra.io.database.migration.MigrationManager;
import org.nitrowater.waterapi.domain.infra.io.file.FileExtension;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityColoredMessageSender;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocitySchedulerService;
import org.nitrowater.waterapi.domain.kernel.ConfigLoader;
import org.nitrowater.waterapi.domain.kernel.Kernel;
import org.nitrowater.waterapi.domain.kernel.LocaleConfig;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.logger.MessageLoggerService;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.placeholder.PlaceholdersContributor;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.service.EventListener;
import org.nitrowater.waterapi.domain.shared.service.SchedulerService;
import org.nitrowater.waterapi.domain.shared.service.Service;
import org.nitrowater.waterapi.domain.spi.*;

import java.io.File;
import java.util.List;

/**
 * Factory that wires and initializes all domain services.
 * <p>
 * Centralizes dependency injection and service lifecycle management.
 * Reads configuration and services from the WaterAPI kernel, registers
 * placeholder contributors and config-reload listeners.
 * </p>
 */
@Getter
public class ServiceFactory {

    private final Kernel kernel;
    private final SchedulerService scheduler;
    private final Database database;
    private final WProxyServer wProxyServer;

    private final ServerService serverService;
    private final ChannelService channelService;
    private final PlayerService playerService;
    private final TabListService tabListService;
    private final BroadcastService broadcastService;
    private final ChatService chatService;
    private final CommandRegistrar commandRegistrar;
    private final CommandHelper commandHelper;
    private final LoadSummaryReporter loadSummaryReporter;

    @Getter
    private final PlayerActionService playerActionService;

    public ServiceFactory(Kernel kernel,
                          Object pluginInstance,
                          WProxyServer wProxyServer,
                          PermissionService permissionService,
                          PlayerActionService actionService,
                          TabListProvider tabListProvider,
                          CommandRegistrar commandRegistrar) {
        this.kernel = kernel;
        // Register VeloChatX's user-owned dynamic config collections (also whitelists them).
        registerConfigSchemas(kernel.getConfigManager());
        this.wProxyServer = wProxyServer;
        // Extract services from kernel
        PlaceholderService placeholderService = kernel.getPlaceholderService();
        MessageLoggerService messageLoggerService = kernel.getMessageLoggerService();
        LoggerService loggerService = kernel.getLoggerService();
        // Load configs from kernel
        Config mainConfig = new Config(kernel.getPluginConfigService().getConfigData());
        File dataFolder = kernel.getPluginContext().getDataFolder();
        // broadcast.yml is self-loaded by BroadcastServiceImpl; hand it the config loader.
        ConfigLoader broadcastConfigLoader = kernel.getConfigLoader(FileExtension.YML);
        LocaleConfig broadcastLocale = kernel.getPluginConfigService().getLocaleConfig();
        // Infrastructure
        this.scheduler = new VelocitySchedulerService(wProxyServer.toNative(), pluginInstance);
        this.database = initDatabase(dataFolder.getAbsolutePath(), loggerService);
        // Domain services
        this.serverService = new ServerServiceImpl(mainConfig, wProxyServer);
        this.channelService = new ChannelServiceImpl(mainConfig, serverService);

        PlayerMapper playerMapper = new PlayerMapper(database);
        PlayerStatusMapper statusMapper = new PlayerStatusMapper(database);
        PlayerBlockedMapper blockedMapper = new PlayerBlockedMapper(database);
        this.playerService = new PlayerServiceImpl(permissionService, playerMapper, statusMapper, blockedMapper);

        this.playerActionService = new PlayerActionService(
                new VelocityColoredMessageSender(wProxyServer.toNative()), placeholderService);

        this.tabListService = new TabListServiceImpl(mainConfig, wProxyServer, placeholderService, tabListProvider, scheduler);

        this.broadcastService = new BroadcastServiceImpl(
                broadcastConfigLoader, broadcastLocale, serverService, channelService, wProxyServer,
                placeholderService, actionService, scheduler, loggerService);

        this.chatService = new ChatServiceImpl(mainConfig, placeholderService, messageLoggerService);

        this.commandRegistrar = commandRegistrar;
        this.commandHelper = new CommandHelper(
                kernel.getMessageService(),
                actionService,
                placeholderService,
                wProxyServer,
                kernel.getLocale(),
                loggerService
        );
        this.loadSummaryReporter = new LoadSummaryReporter(
                serverService,
                channelService,
                broadcastService,
                kernel.getMessageService(),
                loggerService
        );

        List<Service> services = List.of(
                serverService, channelService, playerService,
                tabListService, broadcastService, chatService
        );

        placeholderService.registerContributors(
                serverService,
                channelService,
                playerService
        );

        kernel.registerListeners(
                KernelConfigUpdateEvent.class,
                serverService, chatService, channelService, tabListService, broadcastService
        );

        kernel.registerConfigServices(broadcastService);

        // Full reload (/<cmd> reload) re-reads configs; restart schedulers so interval
        // changes (broadcast / tab-list) take effect without a restart.
        kernel.registerReloadHook(this::restartSchedulers);
    }

    public void startSchedulers() {
        broadcastService.startScheduler();
        tabListService.startConsumer();
    }

    /**
     * Log the one-shot startup self-check summary for the loaded
     * servers, channels and broadcasts. Intended to run once on first load.
     */
    public void reportLoadSummary() {
        loadSummaryReporter.report();
    }

    public void stopSchedulers() {
        broadcastService.stopScheduler();
        tabListService.stopConsumer();
        scheduler.cancelAll();
    }

    /**
     * Reload hook: broadcast / tab-list intervals are captured when their tasks are
     * registered, so a config reload alone does not reschedule them.
     */
    private void restartSchedulers() {
        stopSchedulers();
        startSchedulers();
    }

    public void close() {
        stopSchedulers();
        database.close();
    }

    /**
     * Register the item schemas of VeloChatX's user-owned dynamic collections. Registering a
     * schema also whitelists the collection from config diffs / self-update, so shipped example
     * entries are neither reported as missing nor re-inserted. Validation is read-only and runs
     * once at startup ({@code Kernel#checkConfigContent}).
     */
    private static void registerConfigSchemas(ConfigManager configManager) {
        // config.yml: each channel must declare its servers; the welcome block is optional, but
        // when present it needs both enable and message.
        configManager.registerItemSchema("channels.channel-list",
                KeyItemSchema.builder()
                        .required("servers")
                        .optional("welcome", welcome -> welcome
                                .required("enable")
                                .required("message"))
                        .build());
        // broadcast.yml: each locale broadcast must carry at least a message list.
        configManager.registerItemSchema("locale.broadcast-list",
                KeyItemSchema.builder()
                        .required("message-list")
                        .optional("prefix")
                        .optional("channel-list")
                        .optional("server-list")
                        .build());
        // config.yml: server display names are free-form (whitelist only, no checks).
        configManager.registerItemSchema("server-display.display", ItemSchema.freeform());
    }

    private Database initDatabase(String dataFolderPath, LoggerService loggerService) {
        Database db = new H2DataBase(
                new File(dataFolderPath, "data.db")
        );

        MigrationManager migrationManager = new MigrationManager(db,
                List.of(
                        new V001__CreatePlayerTables(),
                        new V002__CreateIgnoreRejectTables()
                ),
                loggerService
        );

        migrationManager.migrate();

        return db;
    }

    /**
     * Reload {@code broadcast.yml} from disk and refresh the broadcast service.
     */
    public void reloadBroadcast() throws java.io.IOException {
        kernel.reloadConfigService("broadcast");
    }

    /**
     * Reload every configuration source managed by the kernel
     * (main config, broadcast, messages).
     *
     * @throws java.io.IOException if a config file cannot be reloaded
     */
    public void reloadAll() throws java.io.IOException {
        kernel.reloadConfigServices();
    }

    public WProxyServer getWProxyServer() {
        return this.wProxyServer;
    }
}
