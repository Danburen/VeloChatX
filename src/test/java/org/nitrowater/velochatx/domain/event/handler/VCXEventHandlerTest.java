package org.nitrowater.velochatx.domain.event.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.broadcast.BroadcastServiceImpl;
import org.nitrowater.velochatx.domain.channel.ChannelServiceImpl;
import org.nitrowater.velochatx.domain.chat.ChatServiceImpl;
import org.nitrowater.velochatx.domain.event.player.PlayerJoinServerEvent;
import org.nitrowater.velochatx.domain.server.ServerServiceImpl;
import org.nitrowater.velochatx.domain.tablist.TabListUpdateTask;
import org.nitrowater.velochatx.testsupport.FakeColoredMessageSender;
import org.nitrowater.velochatx.testsupport.FakeLoggerService;
import org.nitrowater.velochatx.testsupport.FakeMessageLoggerService;
import org.nitrowater.velochatx.testsupport.FakeMessageService;
import org.nitrowater.velochatx.testsupport.FakePlayerService;
import org.nitrowater.velochatx.testsupport.FakePluginMessageService;
import org.nitrowater.velochatx.testsupport.FakeSchedulerService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.RecordingTabListService;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.event.player.PlayerChatEvent;
import org.nitrowater.waterapi.domain.event.player.PlayerDisconnectEvent;
import org.nitrowater.waterapi.domain.event.player.PlayerLoginEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.common.WColor;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.spi.PlayerActionService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end event flows: chat formatting / ban words / crossing chat, backend join, proxy
 * login and disconnect. Uses the real chat, server, channel and broadcast services; only the
 * persistence, scheduler, tab-list and transport layers are faked.
 */
class VCXEventHandlerTest {

    private FakeWProxyServer proxy;
    private PlaceholderServiceImpl placeholders;
    private FakeColoredMessageSender sender;
    private FakeLoggerService logger;
    private FakeMessageLoggerService messageLogger;
    private FakeMessageService messageService;
    private FakePluginMessageService pluginMessages;
    private FakePlayerService playerService;
    private RecordingTabListService tabList;
    private ServerServiceImpl serverService;
    private ChannelServiceImpl channelService;
    private ChatServiceImpl chatService;
    private BroadcastServiceImpl broadcastService;

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
        placeholders.register("proxy", context -> "[Proxy]");

        sender = new FakeColoredMessageSender();
        logger = new FakeLoggerService();
        pluginMessages = new FakePluginMessageService();
        messageLogger = new FakeMessageLoggerService(logger, pluginMessages, placeholders);
        messageService = new FakeMessageService()
                .put("ban-words-message", "BANNED")
                .put("cant-load-message", "locale failed")
                .put("ban-words-log-message", "{player} | {message}");

        playerService = new FakePlayerService();
        tabList = new RecordingTabListService();
    }

    private Config mainConfig(Object... overrides) {
        Object[] base = new Object[]{
                "server.display-enable", true,
                "server.list.lobby.channels", List.of("login"),
                "server.list.server1.channels", List.of("login"),
                "server.list.server2.channels", List.of("games"),
                "channels.global", false,
                "channels.channel-list.login.display", "[Login]",
                "channels.channel-list.games.display", "[Games]",
                "channels.channel-list.login.welcome.enable", true,
                "channels.channel-list.login.welcome.message", "W {player} @ {server}",
                "chat-format", "{server} {player}: {message}",
                "crossing-chat-enable", false,
                "ban-words.enable", false,
                "log-text.enable", true,
                "log-text.convert", false
        };
        Object[] all = new Object[base.length + overrides.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(overrides, 0, all, base.length, overrides.length);
        return TestConfigs.config(all);
    }

    private static Config broadcastConfig(Object... overrides) {
        return TestConfigs.config(overrides);
    }

    private void build(Config main, Config broadcast) {
        serverService = new ServerServiceImpl(main, proxy);
        channelService = new ChannelServiceImpl(main, serverService);
        chatService = new ChatServiceImpl(main, placeholders, messageLogger);
        broadcastService = new BroadcastServiceImpl(
                null, null, serverService, channelService, proxy, placeholders,
                new PlayerActionService(sender, placeholders), new FakeSchedulerService(), logger);
        broadcastService.updateConfig(broadcast);
    }

    private VCXEventHandler handler() {
        return new VCXEventHandler(chatService, new PlayerActionService(sender, placeholders),
                messageService, channelService, playerService, messageLogger, proxy,
                serverService, broadcastService, tabList);
    }

    private static Config baseBroadcast() {
        return broadcastConfig(
                "global.enable", false,
                "locale.enable", false,
                "welcome-broadcast.enable", true,
                "welcome-broadcast.only-first-join", false,
                "welcome-broadcast.message", "W {player}",
                "entry-broadcast.log-to-console", false,
                "entry-broadcast.hierarchical-overlay", false,
                "entry-broadcast.backend.enable", true,
                "entry-broadcast.backend.scope", "server",
                "entry-broadcast.backend.join-message", "BJ {server}",
                "entry-broadcast.backend.leave-message", "BL {server}",
                "entry-broadcast.channel.enable", false,
                "entry-broadcast.proxy.enable", true,
                "entry-broadcast.proxy.join-message", "PJ {player}",
                "entry-broadcast.proxy.leave-message", "PL {player}");
    }

    // ======================== Chat ========================

    @Test
    void chatIsFormattedAndLoggedWhenEnabled() {
        build(mainConfig(), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        handler().onChat(new PlayerChatEvent(alice, "hello", "lobby"));

        assertEquals(List.of("lobby Alice: hello"), logger.infoMessages);
        assertTrue(sender.playerMessages.isEmpty());
    }

    @Test
    void chatOfflinePlayersAreSilentlyIgnored() {
        build(mainConfig(), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        playerService.setChatOffline(alice.getUuid(), true);

        handler().onChat(new PlayerChatEvent(alice, "hello", "lobby"));

        assertTrue(logger.infoMessages.isEmpty());
        assertTrue(sender.playerMessages.isEmpty());
    }

    @Test
    void bannedWordsNotifyTheSenderAndLogWhenConfigured() {
        build(mainConfig(
                "ban-words.enable", true,
                "ban-words.words", "bad",
                "ban-words.log-to-console", true), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        handler().onChat(new PlayerChatEvent(alice, "a bad word", "lobby"));

        assertEquals(1, sender.playerMessages.size());
        assertEquals("BANNED", sender.playerMessages.get(0).message());
        assertEquals(WColor.RED, sender.playerMessages.get(0).color());
        assertEquals(1, messageLogger.autos.size());
        // The log template carries {player}: the resolved line must start with the sender,
        // not with an empty name.
        assertEquals("Alice | lobby Alice: a bad word", messageLogger.autos.get(0));
        assertTrue(logger.infoMessages.isEmpty(), "blocked chat must not reach the chat log");
    }

    @Test
    void bannedWordLoggingCanBeDisabled() {
        build(mainConfig(
                "ban-words.enable", true,
                "ban-words.words", "bad",
                "ban-words.log-to-console", false), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        handler().onChat(new PlayerChatEvent(alice, "bad", "lobby"));

        assertEquals(1, sender.playerMessages.size());
        assertTrue(messageLogger.autos.isEmpty());
    }

    @Test
    void crossingChatTargetsOnlyCommunicableOnlinePlayers() {
        build(mainConfig("crossing-chat-enable", true, "log-text.enable", false), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "server1");     // same channel
        UPlayer carol = TestPlayers.online(proxy, "Carol", "server2");  // other channel
        UPlayer dave = TestPlayers.online(proxy, "Dave", "server1");    // blocks Alice
        UPlayer eve = TestPlayers.online(proxy, "Eve", "server1");      // chat offline

        playerService.blockPlayer(dave.getUuid(), alice.getUuid());
        playerService.setChatOffline(eve.getUuid(), true);

        handler().onChat(new PlayerChatEvent(alice, "hi", "lobby"));

        assertEquals(List.of("lobby Alice: hi"), sender.playerMessageTexts(bob));
        assertTrue(sender.playerMessageTexts(carol).isEmpty());
        assertTrue(sender.playerMessageTexts(dave).isEmpty());
        assertTrue(sender.playerMessageTexts(eve).isEmpty());
        assertTrue(sender.playerMessageTexts(alice).isEmpty(), "source server is filtered");
    }

    @Test
    void crossingChatUsesTheConfiguredFormatModel() {
        build(mainConfig(
                "crossing-chat-enable", true,
                "chat-format-model", "legacy",
                "log-text.enable", false), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "server1");

        handler().onChat(new PlayerChatEvent(alice, "hi", "lobby"));

        assertEquals(1, sender.componentSends.size());
        assertEquals(bob.getUuid(), sender.componentSends.get(0).player().getUuid());
        assertTrue(sender.playerMessageTexts(bob).isEmpty());
    }

    // ======================== Backend join ========================

    @Test
    void joinServerLoadsLocaleBroadcastsWelcomeAndQueuesTabUpdates() {
        build(mainConfig(), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "server1");

        handler().onJoinServer(new PlayerJoinServerEvent(alice, "lobby", "server1"));

        assertEquals(List.of("en"), pluginMessages.loadedLocales);
        assertEquals(List.of("BL server1"), sender.serverBroadcastTexts("server1"));
        assertEquals(List.of("BJ lobby"), sender.serverBroadcastTexts("lobby"));
        assertEquals(List.of("W Alice @ lobby"), sender.playerMessageTexts(alice));

        assertEquals(2, tabList.queuedOfType(TabListUpdateTask.UpdateType.ENTRY).size());
        assertEquals(2, tabList.queuedOfType(TabListUpdateTask.UpdateType.HEADER_FOOTER).size());
        // Entries are re-added in BOTH directions: every proxy player in the joining player's
        // tab list, and the joining player in every other player's tab list (a backend switch
        // removes the player-info packet on both sides).
        List<TabListUpdateTask> adds = tabList.queuedOfType(TabListUpdateTask.UpdateType.ADD);
        assertEquals(4, adds.size());
        assertTrue(adds.stream().anyMatch(task ->
                task.getPlayerUuid().equals(bob.getUuid())
                        && task.getTargetPlayerUuid().equals(alice.getUuid())));
        assertTrue(adds.stream().anyMatch(task ->
                task.getPlayerUuid().equals(alice.getUuid())
                        && task.getTargetPlayerUuid().equals(bob.getUuid())));
        assertTrue(tabList.queued.stream().anyMatch(task ->
                task.getPlayerUuid().equals(bob.getUuid())
                        && task.getType() == TabListUpdateTask.UpdateType.HEADER_FOOTER));
    }

    @Test
    void localeLoadFailureIsReportedButDoesNotAbortTheJoin() {
        build(mainConfig(), baseBroadcast());
        pluginMessages.failLoadLocale(true);
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        handler().onJoinServer(new PlayerJoinServerEvent(alice, "lobby", null));

        assertTrue(messageLogger.warnings.contains("cant-load-message"));
        assertEquals(List.of("BJ lobby"), sender.serverBroadcastTexts("lobby"));
    }

    @Test
    void firstJoinDoesNotBroadcastLeave() {
        build(mainConfig(), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        handler().onJoinServer(new PlayerJoinServerEvent(alice, "lobby", null));

        assertTrue(sender.serverBroadcastTexts("server2").isEmpty());
        assertEquals(List.of("BJ lobby"), sender.serverBroadcastTexts("lobby"));
    }

    // ======================== Proxy login ========================

    @Test
    void loginBroadcastsProxyJoinAndSendsWelcome() {
        build(mainConfig(), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        handler().onLogin(new PlayerLoginEvent(alice));

        assertEquals(List.of("PJ Alice"), sender.allBroadcasts);
        assertEquals(List.of("W Alice"), sender.playerMessageTexts(alice));
        assertTrue(playerService.getPlayerName(alice.getUuid()).isPresent());
    }

    @Test
    void welcomeOnlyFirstJoinSuppressesRepeatLogins() {
        build(mainConfig(), broadcastConfig(
                "welcome-broadcast.enable", true,
                "welcome-broadcast.only-first-join", true,
                "welcome-broadcast.message", "W {player}",
                "entry-broadcast.proxy.enable", true,
                "entry-broadcast.proxy.join-message", "PJ {player}"));
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        VCXEventHandler handler = handler();

        handler.onLogin(new PlayerLoginEvent(alice));
        assertEquals(List.of("W Alice"), sender.playerMessageTexts(alice));

        sender.playerMessages.clear();
        sender.allBroadcasts.clear();
        handler.onLogin(new PlayerLoginEvent(alice));

        assertEquals(List.of("PJ Alice"), sender.allBroadcasts);
        assertTrue(sender.playerMessageTexts(alice).isEmpty());
    }

    // ======================== Disconnect ========================

    @Test
    void disconnectBroadcastsQueuesRemovalsAndDropsTheAttribution() {
        build(mainConfig(), baseBroadcast());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "server1");
        playerService.getOrCreateAttrs(alice.getUuid());

        handler().onDisconnect(new PlayerDisconnectEvent(alice, "lobby"));

        assertEquals(List.of("PL Alice"), sender.allBroadcasts);
        assertTrue(playerService.leaveTimeUpdates.contains(alice.getUuid()));
        assertTrue(playerService.removedUuids.contains(alice.getUuid()));
        assertTrue(playerService.getPlayerAttribution(alice.getUuid()).isEmpty());

        List<TabListUpdateTask> removals =
                tabList.queuedOfType(TabListUpdateTask.UpdateType.REMOVE);
        assertEquals(2, removals.size());
        assertTrue(removals.stream().allMatch(task ->
                task.getPlayerUuid().equals(alice.getUuid())));
        assertTrue(removals.stream().anyMatch(task ->
                task.getTargetPlayerUuid().equals(bob.getUuid())));
        assertFalse(removals.isEmpty());
        // The online counts change on leave: remaining viewers get a header/footer refresh
        // instead of waiting for the 5-minute fallback.
        assertTrue(tabList.queued.stream().anyMatch(task ->
                task.getPlayerUuid().equals(bob.getUuid())
                        && task.getType() == TabListUpdateTask.UpdateType.HEADER_FOOTER));
    }
}
