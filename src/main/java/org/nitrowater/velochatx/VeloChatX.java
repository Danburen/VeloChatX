package org.nitrowater.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.nitrowater.velochatx.commands.ControlCommandHandler;
import org.nitrowater.velochatx.commands.MentionCommandHandler;
import org.nitrowater.velochatx.commands.MsgCommandHandler;
import org.nitrowater.velochatx.domain.event.VeloChatXGameEventHandler;
import org.nitrowater.velochatx.domain.event.handler.VCXEventHandler;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.events.PlayerEventManager;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityPlayerPlatformStrategy;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityWProxyServerModel;
import org.nitrowater.waterapi.domain.kernel.Kernel;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.spi.*;
import org.nitrowater.waterapi.plugin.WaterPlugin;

import java.util.List;

/**
 * VeloChatX main plugin class.
 * <p>
 * A Velocity chat plugin providing cross-server messaging,
 * mentions, and chat formatting.
 * </p>
 *
 * @since 2.0.0
 * @author Danburen
 */
@Plugin(
        id = "velochatx",
        name = "VeloChatX",
        authors = "Waterwood",
        version = "2.1.0")
public class VeloChatX extends WaterPlugin {

    private final Metrics.Factory metricsFactory;
    private ServiceFactory serviceFactory;
    private final ProxyServer proxyServer;

    @Inject
    public VeloChatX(ProxyServer server, Metrics.Factory metricsFactory) {
        super();
        this.metricsFactory = metricsFactory;
        this.proxyServer = server;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        long start = System.currentTimeMillis();

        // Check if plugin is enabled
        boolean enabled = getKernel().getPluginConfigService()
                .getConfigData().getBoolean("enable", true);

        if (!enabled) {
            getKernel().getLoggerService().warning("Plugin is disabled");
            return;
        }

        // Initialize services
        init();
        long elapsed = System.currentTimeMillis() - start;
        getKernel().getLoggerService().info("VeloChatX enabled in " + elapsed + "ms");

        // Metrics
        Metrics metrics = metricsFactory.make(this, 23273);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (serviceFactory != null) {
            serviceFactory.close();
        }
    }

    private void init() {
        Kernel kernel = getKernel();
        VelocityPlayerPlatformStrategy strategy = new VelocityPlayerPlatformStrategy();
        WProxyServer wProxyServer = new VelocityWProxyServerModel(proxyServer, strategy);
        // Create Velocity SPI implementations
        PermissionService permissionService = new org.nitrowater.waterapi.domain.infra.velocity.adapter.SafePermissionService();
        ColoredMessageSender messageSender = new org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityColoredMessageSender(proxyServer);
        PlayerActionService actionService = new org.nitrowater.waterapi.domain.spi.PlayerActionService(messageSender, kernel.getPlaceholderService());
        TabListProvider tabListProvider = new org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityTabListProvider(proxyServer);
        CommandRegistrar commandRegistrar = new org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityCommandRegistrar(proxyServer);
        // Create service factory
        this.serviceFactory = new ServiceFactory(
                kernel, this, wProxyServer,
                permissionService, actionService, tabListProvider, commandRegistrar
        );
        serviceFactory.startSchedulers();
        // Register event handlers
        VCXEventHandler eventHandler = new org.nitrowater.velochatx.domain.event.handler.VCXEventHandler(
                serviceFactory.getChatService(),
                actionService,
                actionService,
                kernel.getMessageService(),
                serviceFactory.getChannelService(),
                serviceFactory.getPlayerService(),
                kernel.getMessageLoggerService(),
                wProxyServer,
                serviceFactory.getServerService(),
                serviceFactory.getBroadcastService(),
                serviceFactory.getTabListService()
        );

        PlayerEventManager playerEventManager = new org.nitrowater.velochatx.events.PlayerEventManager(
                new org.nitrowater.waterapi.domain.model.UPlayerRegistry(
                        strategy,
                        wProxyServer
                ),
                eventHandler
        );
        proxyServer.getEventManager().register(this, playerEventManager);
        // Register commands
        registerCommands();
        // Check for updates
        kernel.checkUpdate("Danburen");
    }

    private void registerCommands() {
        var registrar = serviceFactory.getCommandRegistrar();

        registrar.register(
                "velochatx",
                new ControlCommandHandler(serviceFactory, getKernel().getMessageLoggerService()),
                "velochatx.player",
                List.of("vcx", "chatx", "vc")
        );

        registrar.register("mention",
                new MentionCommandHandler(serviceFactory),
                "velochatx.player",
                List.of("mention", "men", "at")
        );

        registrar.register("vcmsg",
                new MsgCommandHandler(serviceFactory),
                "velochatx.player",
                List.of("vcmsg", "vctell")
        );
    }

    /**
     * Get the service factory.
     *
     * @return the service factory
     */
    public ServiceFactory getServiceFactory() {
        return serviceFactory;
    }
}
