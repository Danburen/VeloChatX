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
import org.nitrowater.waterapi.domain.infra.io.database.Database;
import org.nitrowater.waterapi.domain.infra.io.database.H2DataBase;
import org.nitrowater.waterapi.domain.infra.io.database.migration.MigrationManager;
import org.nitrowater.waterapi.domain.infra.io.file.FileExtension;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityColoredMessageSender;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocitySchedulerService;
import org.nitrowater.waterapi.domain.kernel.Kernel;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.logger.MessageLoggerService;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.service.SchedulerService;
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
        this.wProxyServer = wProxyServer;
        // Extract services from kernel
        PlaceholderService placeholderService = kernel.getPlaceholderService();
        MessageLoggerService messageLoggerService = kernel.getMessageLoggerService();
        LoggerService loggerService = kernel.getLoggerService();
        // Load configs from kernel
        Config mainConfig = new Config(kernel.getPluginConfigService().getConfigData());
        File dataFolder = kernel.getPluginContext().getDataFolder();
        Config broadcastConfig = loadBroadcastConfig();
        // Infrastructure
        this.scheduler = new VelocitySchedulerService(wProxyServer.toNative(), pluginInstance);
        this.database = initDatabase(dataFolder.getAbsolutePath());
        // Domain services
        ServerServiceImpl serverServiceImpl = new ServerServiceImpl(mainConfig, wProxyServer);
        this.serverService = serverServiceImpl;
        ChannelServiceImpl channelServiceImpl = new ChannelServiceImpl(mainConfig, serverServiceImpl);
        this.channelService = channelServiceImpl;

        PlayerMapper playerMapper = new PlayerMapper(database);
        PlayerStatusMapper statusMapper = new PlayerStatusMapper(database);
        PlayerBlockedMapper blockedMapper = new PlayerBlockedMapper(database);
        PlayerServiceImpl playerServiceImpl =
                new PlayerServiceImpl(permissionService, playerMapper, statusMapper, blockedMapper);
        this.playerService = playerServiceImpl;

        this.playerActionService = new PlayerActionService(
                new VelocityColoredMessageSender(wProxyServer.toNative()), placeholderService);

        TabListServiceImpl tabListServiceImpl =
                new TabListServiceImpl(mainConfig, wProxyServer, placeholderService, tabListProvider, scheduler);
        this.tabListService = tabListServiceImpl;

        BroadcastServiceImpl broadcastServiceImpl = new BroadcastServiceImpl(
                broadcastConfig, serverServiceImpl, channelServiceImpl, wProxyServer,
                placeholderService, actionService, scheduler, loggerService);
        this.broadcastService = broadcastServiceImpl;

        ChatServiceImpl chatServiceImpl = new ChatServiceImpl(mainConfig, placeholderService, messageLoggerService);
        this.chatService = chatServiceImpl;

        this.commandRegistrar = commandRegistrar;
        this.commandHelper = new CommandHelper(
                kernel.getMessageService(),
                actionService,
                placeholderService,
                wProxyServer,
                kernel.getLocale(),
                loggerService
        );

        // Register placeholder contributors ({server}, {player}, {ping}, {channel}, ...)
        placeholderService.registerContributor(serverServiceImpl);
        placeholderService.registerContributor(channelServiceImpl);
        placeholderService.registerContributor(playerServiceImpl);

        // Register config-reload listeners
        kernel.registerListener(KernelConfigUpdateEvent.class, serverServiceImpl);
        kernel.registerListener(KernelConfigUpdateEvent.class, channelServiceImpl);
        kernel.registerListener(KernelConfigUpdateEvent.class, chatServiceImpl);
        kernel.registerListener(KernelConfigUpdateEvent.class, tabListServiceImpl);
        kernel.registerListener(KernelConfigUpdateEvent.class, broadcastServiceImpl);
    }

    public void startSchedulers() {
        broadcastService.startScheduler();
        tabListService.startConsumer();
    }

    public void stopSchedulers() {
        broadcastService.stopScheduler();
        tabListService.stopConsumer();
        scheduler.cancelAll();
    }

    public void close() {
        stopSchedulers();
        database.close();
    }

    private Database initDatabase(String dataFolderPath) {
        Database db = new H2DataBase(
                new File(dataFolderPath, "data.db")
        );

        MigrationManager migrationManager = new MigrationManager(db,
                List.of(
                        new V001__CreatePlayerTables(),
                        new V002__CreateIgnoreRejectTables()
                )
        );

        migrationManager.migrate();

        return db;
    }

    private Config loadBroadcastConfig() {
        var configData = kernel.createAndLoadConfig("broadcast", FileExtension.YML);
        if (configData != null) {
            return new Config(configData);
        }
        kernel.getLoggerService().warning("Failed to load broadcast config: "
                + kernel.getPluginContext().getDataFolder());
        return null;
    }

    /**
     * Reload {@code broadcast.yml} from disk and refresh the broadcast service.
     */
    public void reloadBroadcast() {
        var configData = kernel.createAndLoadConfig("broadcast", FileExtension.YML);
        if (configData != null) {
            broadcastService.updateConfig(new Config(configData));
        }
    }

    /**
     * Reload every configuration source (main config, broadcast, messages).
     *
     * @throws java.io.IOException if the message file cannot be reloaded
     */
    public void reloadAll() throws java.io.IOException {
        kernel.getPluginConfigService().reload();
        reloadBroadcast();
        if (kernel.getMessageService() != null) {
            kernel.getMessageService().reload();
        }
    }

    public WProxyServer getWProxyServer() {
        return this.wProxyServer;
    }
}
