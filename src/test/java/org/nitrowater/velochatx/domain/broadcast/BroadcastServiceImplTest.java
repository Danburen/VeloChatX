package org.nitrowater.velochatx.domain.broadcast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.channel.ChannelServiceImpl;
import org.nitrowater.velochatx.domain.server.ServerServiceImpl;
import org.nitrowater.velochatx.testsupport.FakeColoredMessageSender;
import org.nitrowater.velochatx.testsupport.FakeLoggerService;
import org.nitrowater.velochatx.testsupport.FakeSchedulerService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.config.application.SourceType;
import org.nitrowater.waterapi.domain.kernel.ConfigLoader;
import org.nitrowater.waterapi.domain.kernel.LocaleConfig;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.config.ConfigData;
import org.nitrowater.waterapi.domain.spi.PlayerActionService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards broadcast model loading (global + locale), scope resolution for join/leave events,
 * proxy broadcasts, and timed-broadcast scheduling.
 */
class BroadcastServiceImplTest {

    private FakeWProxyServer proxy;
    private PlaceholderServiceImpl placeholders;
    private FakeColoredMessageSender sender;
    private FakeSchedulerService scheduler;
    private FakeLoggerService logger;
    private ServerServiceImpl serverService;
    private ChannelServiceImpl channelService;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
        proxy.registerServer("lobby");
        proxy.registerServer("server1");
        proxy.registerServer("server2");

        placeholders = new PlaceholderServiceImpl();
        placeholders.register("server", context -> context.getServerName().orElse(""));
        placeholders.register("player", context ->
                context.getPlayer().map(UPlayer::getLoginName).orElse(""));

        sender = new FakeColoredMessageSender();
        scheduler = new FakeSchedulerService();
        logger = new FakeLoggerService();

        Config main = TestConfigs.config(
                "channels.global", false,
                "server.list.lobby.channels", List.of("login"),
                "server.list.server1.channels", List.of("login"),
                "server.list.server2.channels", List.of("games"),
                "channels.channel-list.login.display", "[Login]",
                "channels.channel-list.games.display", "[Games]");
        serverService = new ServerServiceImpl(main, proxy);
        channelService = new ChannelServiceImpl(main, serverService);
    }

    private BroadcastServiceImpl service(Config broadcastConfig) {
        BroadcastServiceImpl service = new BroadcastServiceImpl(
                null, null, serverService, channelService, proxy, placeholders,
                new PlayerActionService(sender, placeholders), scheduler, logger);
        service.updateConfig(broadcastConfig);
        return service;
    }

    private static Config globalOnly(Object... extraPairs) {
        Object[] pairs = new Object[extraPairs.length + 4];
        pairs[0] = "global.enable";
        pairs[1] = true;
        pairs[2] = "locale.enable";
        pairs[3] = false;
        System.arraycopy(extraPairs, 0, pairs, 4, extraPairs.length);
        return TestConfigs.config(pairs);
    }

    @Test
    void loadsGlobalAndLocaleBroadcasts() {
        Config config = TestConfigs.config(
                "global.enable", true,
                "global.prefix", "[G]",
                "global.message-list", List.of("gm1", "gm2"),
                "locale.enable", true,
                "locale.broadcast-list", Map.of(
                        "loginBct", Map.of(
                                "enable", true,
                                "prefix", "[L]",
                                "message-list", List.of("lm1", "gm1"),
                                "channel-list", List.of("login")),
                        "offBct", Map.of(
                                "enable", false,
                                "message-list", List.of("never"))));
        BroadcastServiceImpl service = service(config);

        BroadcastModel global = service.getGlobalBroadcast().orElseThrow();
        assertEquals("global", global.getName());
        assertEquals("[G]", global.getPrefix());
        assertEquals(List.of("gm1", "gm2"), global.getMessages());

        assertEquals(1, service.getLocaleBroadcasts().size());
        assertTrue(service.getLocaleBroadcast("loginBct").isPresent());
        assertTrue(service.getLocaleBroadcast("offBct").isEmpty());

        // Locale channel membership expands to the channel's servers.
        assertEquals(List.of("lobby", "server1"),
                service.getLocaleBroadcast("loginBct").orElseThrow().getServers().stream()
                        .map(org.nitrowater.waterapi.domain.model.UServer::getName).toList());

        assertEquals(List.of("gm1", "gm2", "lm1"), service.getMessages("lobby"));
        assertEquals(List.of("gm1", "gm2"), service.getMessages("server2"));
        // Locale prefix overrides the global one for its servers.
        assertEquals("[L]", service.getPrefix("lobby"));
        assertEquals("[G]", service.getPrefix("server2"));
        assertEquals(3, service.getMessageCount("lobby"));
        assertEquals(2, service.getMessageCount("server2"));
        assertEquals(2, service.getBroadcastsForServer("lobby").size());
        assertEquals(1, service.getBroadcastsForServer("server2").size());
        assertEquals("broadcast", service.getConfigBaseName());
        assertNotNull(service.getConfigData());
    }

    @Test
    void disabledSectionsProduceNoModels() {
        BroadcastServiceImpl service = service(TestConfigs.config(
                "global.enable", false,
                "locale.enable", false));

        assertTrue(service.getGlobalBroadcast().isEmpty());
        assertTrue(service.getLocaleBroadcasts().isEmpty());
        assertTrue(service.getMessages("lobby").isEmpty());
        assertEquals("", service.getPrefix("lobby"));
    }

    @Test
    void localeServerAndChannelListsAreValidated() {
        BroadcastServiceImpl service = service(TestConfigs.config(
                "global.enable", false,
                "locale.enable", true,
                "locale.broadcast-list", Map.of("entry", Map.of(
                        "enable", true,
                        "prefix", "[E]",
                        "message-list", List.of("m"),
                        "server-list", List.of("lobby", "ghost", "  "),
                        "channel-list", List.of("missing")))));

        BroadcastModel entry = service.getLocaleBroadcast("entry").orElseThrow();
        assertEquals(List.of("lobby"),
                entry.getServers().stream()
                        .map(org.nitrowater.waterapi.domain.model.UServer::getName).toList());
        assertTrue(service.getBroadcastsForServer("server2").isEmpty());
    }

    @Test
    void backendJoinUsesServerScopeAndLogsToConsole() {
        BroadcastServiceImpl service = service(globalOnly(
                "entry-broadcast.hierarchical-overlay", true,
                "entry-broadcast.backend.enable", true,
                "entry-broadcast.backend.scope", "server",
                "entry-broadcast.backend.join-message", "BJ {server}",
                "entry-broadcast.backend.leave-message", "BL {server}",
                "entry-broadcast.channel.enable", true,
                "entry-broadcast.channel.join-message", "CJ {server}",
                "entry-broadcast.channel.leave-message", "CL {server}",
                "entry-broadcast.proxy.enable", true,
                "entry-broadcast.proxy.join-message", "PJ {player}",
                "entry-broadcast.proxy.leave-message", "PL {player}"));

        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        service.broadcastPlayerJoin(alice,
                serverService.getServer("lobby").orElseThrow(), null);

        assertEquals(List.of("BJ lobby"), sender.serverBroadcastTexts("lobby"));
        assertTrue(logger.infoMessages.contains("[Broadcast] BJ lobby"));
    }

    @Test
    void channelSwitchUsesTheEnteredChannelAndSuppressesBackendWithOverlay() {
        BroadcastServiceImpl service = service(globalOnly(
                "entry-broadcast.hierarchical-overlay", true,
                "entry-broadcast.backend.enable", true,
                "entry-broadcast.backend.scope", "server",
                "entry-broadcast.backend.join-message", "BJ {server}",
                "entry-broadcast.channel.enable", true,
                "entry-broadcast.channel.scope", "channel",
                "entry-broadcast.channel.join-message", "CJ {server}"));

        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        service.broadcastPlayerJoin(alice,
                serverService.getServer("lobby").orElseThrow(),
                serverService.getServer("server2").orElseThrow());

        // JOIN goes to the channel being entered: lobby + server1 are in "login".
        assertEquals(List.of("CJ lobby"), sender.serverBroadcastTexts("lobby"));
        assertEquals(List.of("CJ lobby"), sender.serverBroadcastTexts("server1"));
        assertTrue(sender.serverBroadcastTexts("server2").isEmpty());
    }

    @Test
    void overlayDisabledEmitsChannelAndBackendLevels() {
        BroadcastServiceImpl service = service(globalOnly(
                "entry-broadcast.hierarchical-overlay", false,
                "entry-broadcast.backend.enable", true,
                "entry-broadcast.backend.scope", "server",
                "entry-broadcast.backend.join-message", "BJ {server}",
                "entry-broadcast.channel.enable", true,
                "entry-broadcast.channel.scope", "channel",
                "entry-broadcast.channel.join-message", "CJ {server}"));

        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        service.broadcastPlayerJoin(alice,
                serverService.getServer("lobby").orElseThrow(),
                serverService.getServer("server2").orElseThrow());

        assertEquals(List.of("CJ lobby", "BJ lobby"), sender.serverBroadcastTexts("lobby"));
    }

    @Test
    void leaveTargetsTheChannelAndServerBeingLeft() {
        BroadcastServiceImpl service = service(globalOnly(
                "entry-broadcast.hierarchical-overlay", false,
                "entry-broadcast.backend.enable", true,
                "entry-broadcast.backend.scope", "server",
                "entry-broadcast.backend.leave-message", "BL {server}",
                "entry-broadcast.channel.enable", true,
                "entry-broadcast.channel.scope", "channel",
                "entry-broadcast.channel.leave-message", "CL {server}"));

        // The player has already moved to lobby; the leave event still targets server2.
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        service.broadcastPlayerLeave(alice, serverService.getServer("server2").orElseThrow());

        assertEquals(List.of("CL server2", "BL server2"), sender.serverBroadcastTexts("server2"));
        assertTrue(sender.serverBroadcastTexts("lobby").isEmpty());
    }

    @Test
    void proxyJoinAndLeaveBroadcastToEveryone() {
        BroadcastServiceImpl service = service(globalOnly(
                "entry-broadcast.proxy.enable", true,
                "entry-broadcast.proxy.join-message", "PJ {player}",
                "entry-broadcast.proxy.leave-message", "PL {player}"));

        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        service.broadcastProxyJoin(alice);
        service.broadcastProxyLeave(alice);

        assertEquals(List.of("PJ Alice", "PL Alice"), sender.allBroadcasts);
    }

    @Test
    void disabledProxyBroadcastDoesNothing() {
        BroadcastServiceImpl service = service(globalOnly(
                "entry-broadcast.proxy.enable", false,
                "entry-broadcast.proxy.join-message", "PJ {player}"));

        service.broadcastProxyJoin(TestPlayers.online(proxy, "Alice", "lobby"));

        assertTrue(sender.allBroadcasts.isEmpty());
    }

    @Test
    void schedulerBroadcastsOncePerServer() {
        BroadcastServiceImpl service = service(globalOnly(
                "interval", 10L,
                "global.prefix", "[G]",
                "global.message-list", List.of("M1", "M2")));

        service.startScheduler();

        assertEquals(1, scheduler.tasks.size());
        FakeSchedulerService.Task task = scheduler.lastTask();
        assertEquals(10, task.delay());
        assertEquals(10, task.period());
        assertEquals(TimeUnit.SECONDS, task.timeUnit());

        task.run();

        assertEquals(3, sender.serverBroadcasts.size());
        assertEquals(3, sender.serverBroadcasts.stream()
                .filter(broadcast -> broadcast.message().startsWith("[G]")).count());

        service.stopScheduler();
        assertTrue(task.isCancelled());
    }

    @Test
    void schedulerIsIdempotentAndStoppable() {
        BroadcastServiceImpl service = service(globalOnly(
                "interval", 5L, "global.message-list", List.of("M")));

        service.startScheduler();
        service.startScheduler();
        assertEquals(1, scheduler.tasks.size());

        service.stopScheduler();
        assertTrue(scheduler.lastTask().isCancelled());
        service.stopScheduler();
        assertEquals(1, scheduler.tasks.size());
    }

    @Test
    void nonPositiveIntervalDisablesTimedBroadcastInsteadOfCrashing() {
        BroadcastServiceImpl service = service(globalOnly(
                "interval", 0L, "global.message-list", List.of("M")));

        service.startScheduler();

        assertTrue(scheduler.tasks.isEmpty());
        assertTrue(logger.hasWarningContaining("must be positive"));
    }

    @Test
    void disabledSectionsDoNotSchedule() {
        BroadcastServiceImpl service = service(TestConfigs.config(
                "global.enable", false,
                "locale.enable", false,
                "interval", 10L));

        service.startScheduler();

        assertTrue(scheduler.tasks.isEmpty());
    }

    @Test
    void missingConfigLoaderFallsBackToDefaults() {
        BroadcastServiceImpl service = new BroadcastServiceImpl(
                null, null, serverService, channelService, proxy, placeholders,
                new PlayerActionService(sender, placeholders), scheduler, logger);

        assertNotNull(service.getConfig());
        assertTrue(logger.hasWarningContaining("Broadcast config loader unavailable"));
        // Empty config uses the key defaults, so global is enabled with an empty message list.
        assertTrue(service.getGlobalBroadcast().isPresent());
    }

    @Test
    void unreadableConfigLoaderFallsBackToDefaults() {
        ConfigLoader failing = new ConfigLoader() {
            @Override
            public ConfigData load(ConfigData origin, String baseName, SourceType sourceType) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public ConfigData load(String baseName, SourceType sourceType) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public ConfigData load(String baseName, SourceType sourceType, java.util.Locale locale) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public ConfigData loadAll(SourceType sourceType, String... baseNames) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public ConfigData loadOrCreate(String baseName, List<String> languageChain) throws IOException {
                throw new IOException("boom");
            }
        };

        BroadcastServiceImpl service = new BroadcastServiceImpl(
                failing, new LocaleConfig("en", List.of("en")),
                serverService, channelService, proxy, placeholders,
                new PlayerActionService(sender, placeholders), scheduler, logger);

        assertNotNull(service.getConfig());
        assertTrue(logger.hasWarningContaining("Broadcast config is unavailable"));
    }

    @Test
    void nullConfigUpdateIsIgnored() {
        BroadcastServiceImpl service = service(globalOnly("global.message-list", List.of("M")));
        Config before = service.getConfig();

        service.updateConfig(null);

        assertSame(before, service.getConfig());
    }

    @Test
    void onEventRebuildsModelsFromTheCurrentConfig() {
        BroadcastServiceImpl service = service(globalOnly("global.message-list", List.of("M")));
        assertFalse(service.getMessages("lobby").isEmpty());

        service.onEvent(new KernelConfigUpdateEvent(TestConfigs.config(
                "global.enable", false)));

        // broadcast.yml is a separate config service: the event triggers a model rebuild from
        // the stored config, not from the main-config event payload.
        assertFalse(service.getMessages("lobby").isEmpty());
    }
}
