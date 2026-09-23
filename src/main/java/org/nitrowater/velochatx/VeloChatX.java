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
import org.nitrowater.velochatx.domain.shared.VeloChatXPermissions;
import org.nitrowater.velochatx.events.PlayerEventManager;
import org.nitrowater.waterapi.domain.command.CommandManager;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.SafePermissionService;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityColoredMessageSender;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityCommandRegistrar;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityPlayerPlatformStrategy;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityTabListProvider;
import org.nitrowater.waterapi.domain.infra.velocity.adapter.VelocityWProxyServerModel;
import org.nitrowater.waterapi.domain.kernel.Kernel;
import org.nitrowater.waterapi.domain.model.UPlayerRegistry;
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
        authors = "Danburen",
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
        metricsFactory.make(this, 23273);
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
        PermissionService permissionService = new SafePermissionService();
        ColoredMessageSender messageSender = new VelocityColoredMessageSender(proxyServer);
        PlayerActionService actionService = new PlayerActionService(messageSender, kernel.getPlaceholderService());
        TabListProvider tabListProvider = new VelocityTabListProvider(proxyServer);
        CommandRegistrar commandRegistrar = new VelocityCommandRegistrar(proxyServer);
        // Create service factory
        this.serviceFactory = new ServiceFactory(
                kernel, this, wProxyServer,
                permissionService, actionService, tabListProvider, commandRegistrar
        );
        // One-shot startup self-check: report loaded servers / channels / broadcasts.
        serviceFactory.reportLoadSummary();
        // Report server-display / channel binding mismatches at WARNING level (unbound backends,
        // phantom entries, dead channel servers) so degraded chat formatting is not silent.
        serviceFactory.reportBindingProblems();
        serviceFactory.startSchedulers();
        // Register event handlers
        VCXEventHandler eventHandler = new VCXEventHandler(
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

        PlayerEventManager playerEventManager = new PlayerEventManager(
                new UPlayerRegistry(strategy, wProxyServer),
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
                VeloChatXPermissions.ADMIN
        );

        // The primary command is registered as a TREE: every subcommand carries its own
        // permission, so a platform (Velocity renders Brigadier `requires(...)`) exposes only the
        // subcommands a source may use — no unauthorized subcommand shows up in the client
        // command tree or tab completion. `help` is unrestricted; `config`/`reload` require
        // `velochatx.admin`. Handlers keep their internal checks as defence in depth.
        ControlCommandHandler controlHandler = new ControlCommandHandler(serviceFactory);
        commands.registerTree(
                "velochatx",
                List.of("vcx", "chatx", "vc"),
                controlHandler.buildTree(),
                null
        );

        // mention / vcmsg are single commands whose permission lives at the root, so a plain
        // permission-scoped registration is enough: Velocity hides the node from unauthorized
        // sources while tab completion (player names) is preserved.
        commands.register("mention",
                new MentionCommandHandler(serviceFactory),
                VeloChatXPermissions.PLAYER,
                List.of("mention", "men", "at")
        );

        commands.register("vcmsg",
                new MsgCommandHandler(serviceFactory),
                VeloChatXPermissions.PLAYER,
                List.of("vcmsg", "vctell")
        );

        // Debug-only hint (invisible unless `debug: true`): Velocity itself has no OP concept, so
        // these nodes must be granted through a proxy permission plugin such as LuckPerms.
        // Uses the message-only debug overload on purpose: the node names live in single quotes,
        // which MessageFormat would treat as escapes.
        getKernel().getLoggerService().debug(
                "VeloChatX commands ready. Velocity has no operator status; grant permission nodes "
                        + "velochatx.admin (config/reload/status/show-channel-info/placeholder) and "
                        + "velochatx.player (on/off/block/unblock/list) through a proxy permission "
                        + "plugin such as LuckPerms.");
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
