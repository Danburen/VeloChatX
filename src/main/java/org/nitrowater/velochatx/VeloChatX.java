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
import org.nitrowater.velochatx.domain.event.handler.VCXEventHandler;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.events.PlayerEventManager;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityPlayerPlatformStrategy;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityWProxyServerModel;
import org.nitrowater.waterapi.domain.command.CommandManager;
import org.nitrowater.waterapi.domain.kernel.Kernel;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.shared.common.KernelMessage;
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
        // Enable the kernel in the initialization phase so all of this plugin's startup
        // output (banner, config, services, enable message) stays in one contiguous block
        // instead of being split across the construction phase.
        getKernel().enable();
        // The kernel already validated config and disabled itself when `enable: false`.
        if (getKernel().isDisabled()) {
            return;
        }
        // Initialize services
        init();
        long elapsed = System.currentTimeMillis() - start;
        // Localized enable message (i18n); renders from default/{locale}.properties.
        getKernel().getMessageLoggerService().auto(
                KernelMessage.SUCCESSFULLY_ENABLE_MESSAGE, elapsed
        );
        // Metrics
        Metrics metrics = metricsFactory.make(this, 23273);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (serviceFactory != null) {
            serviceFactory.close();
        }
        this.disable();
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
        // One-shot startup self-check: report loaded servers / channels / broadcasts.
        serviceFactory.reportLoadSummary();
        serviceFactory.startSchedulers();
        // Register event handlers
        VCXEventHandler eventHandler = new org.nitrowater.velochatx.domain.event.handler.VCXEventHandler(
                serviceFactory.getChatService(),
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
        // Report config content problems (missing keys) with an update hint
        kernel.checkConfigContent();
        // Check for updates
        kernel.checkUpdate();
    }

    private void registerCommands() {
        CommandManager commands = getKernel().initCommandManager(
                serviceFactory.getCommandRegistrar(),
                "velochatx",
                List.of("vcx", "chatx", "vc"),
                "velochatx.admin"
        );

        // Primary command requires `velochatx.player` (granted broadly; LP inheritance makes
        // `velochatx.admin` include it). `help` stays reachable for every player that has it.
        commands.registerPrimary(
                new ControlCommandHandler(serviceFactory),
                "velochatx.player"
        );

        commands.register("mention",
                new MentionCommandHandler(serviceFactory),
                "velochatx.player",
                List.of("mention", "men", "at")
        );

        commands.register("vcmsg",
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
