package org.nitrowater.velochatx.domain.channel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.server.ServerServiceImpl;
import org.nitrowater.velochatx.domain.shared.VeloChatXMetaInfoType;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards channel loading (legacy vs server-centric membership), communication rules and
 * placeholders.
 */
class ChannelServiceImplTest {

    private FakeWProxyServer proxy;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
        proxy.registerServer("lobby");
        proxy.registerServer("server1");
        proxy.registerServer("server2");
        proxy.registerServer("other");
    }

    private ServerServiceImpl servers(Config config) {
        return new ServerServiceImpl(config, proxy);
    }

    @Test
    void legacyMembershipWinsWhenAnyChannelDeclaresServers() {
        Config config = TestConfigs.config(
                "channels.global", false,
                "channels.channel-list.login.display", "[Login]",
                "channels.channel-list.login.welcome.enable", true,
                "channels.channel-list.login.welcome.message", "welcome!",
                "channels.channel-list.login.servers", List.of("lobby", "server1", "ghost"),
                // Server-centric membership must be ignored while legacy lists are present.
                "server.list.lobby.channels", List.of("games"),
                "server.list.server2.channels", List.of("games"),
                "channels.channel-list.games.display", "[Games]");
        ServerServiceImpl serverService = servers(config);
        ChannelServiceImpl channels = new ChannelServiceImpl(config, serverService);

        Channel login = channels.getChannel("login").orElseThrow();
        assertEquals("[Login]", login.getDisplayName());
        assertTrue(login.isWelcomeEnabled());
        assertEquals("welcome!", login.getWelcomeMessage());
        assertEquals(List.of("lobby", "server1"),
                login.getServers().stream()
                        .map(org.nitrowater.waterapi.domain.model.UServer::getName).toList());

        assertTrue(channels.canCommunicate("lobby", "server1"));
        assertFalse(channels.canCommunicate("lobby", "server2"));
        assertFalse(channels.canCommunicate("server2", "other"));

        assertEquals("login", channels.getChannelForServer("lobby").orElseThrow().getName());
        assertTrue(channels.getChannelForServer("other").isEmpty());

        assertTrue(serverService.getServer("lobby").orElseThrow()
                .<List<String>>getMeta(VeloChatXMetaInfoType.CHANNELS)
                .orElseThrow().contains("login"));
    }

    @Test
    void serverCentricMembershipIsUsedWhenNoLegacyListsExist() {
        Config config = TestConfigs.config(
                "channels.global", false,
                "channels.channel-list.login.display", "[Login]",
                "channels.channel-list.games.display", "[Games]",
                "server.list.lobby.channels", List.of("login"),
                "server.list.server1.channels", List.of("login", "games"),
                "server.list.server2.channels", List.of("games"),
                "server.list.other.display", "[Other]");
        ServerServiceImpl serverService = servers(config);
        ChannelServiceImpl channels = new ChannelServiceImpl(config, serverService);

        assertEquals(Set.of("lobby", "server1"),
                new java.util.HashSet<>(channels.getChannel("login").orElseThrow().getServers().stream()
                        .map(org.nitrowater.waterapi.domain.model.UServer::getName).toList()));
        assertEquals(Set.of("server1", "server2"),
                new java.util.HashSet<>(channels.getChannel("games").orElseThrow().getServers().stream()
                        .map(org.nitrowater.waterapi.domain.model.UServer::getName).toList()));

        assertEquals("login", channels.getChannelForServer("lobby").orElseThrow().getName());
        assertEquals("games", channels.getChannelForServer("server2").orElseThrow().getName());
        assertTrue(channels.getChannelForServer("other").isEmpty());

        assertTrue(channels.canCommunicate("lobby", "server1"));
        assertTrue(channels.canCommunicate("server1", "server2"));
        assertFalse(channels.canCommunicate("lobby", "server2"));

        assertTrue(serverService.getServer("server1").orElseThrow()
                .<List<String>>getMeta(VeloChatXMetaInfoType.CHANNELS)
                .orElseThrow().containsAll(List.of("login", "games")));
    }

    @Test
    void globalModeAllowsResolvedPairsAndFailsClosedOnUnknown() {
        Config config = TestConfigs.config("channels.global", true);
        ChannelServiceImpl channels = new ChannelServiceImpl(config, servers(config));

        assertTrue(channels.canCommunicate("lobby", "server2"));
        assertFalse(channels.canCommunicate("lobby", "missing"));
        assertFalse(channels.canCommunicate("missing", "lobby"));
        assertFalse(channels.canCommunicate((org.nitrowater.waterapi.domain.model.UServer) null, null));
    }

    @Test
    void legacyUnknownServersAreDroppedAndLeaveAnEmptyChannel() {
        Config config = TestConfigs.config(
                "channels.channel-list.login.servers", List.of("ghost"));
        ChannelServiceImpl channels = new ChannelServiceImpl(config, servers(config));

        Channel login = channels.getChannel("login").orElseThrow();
        assertTrue(login.getServers().isEmpty());
        assertFalse(channels.canCommunicate("ghost", "ghost"));
    }

    @Test
    void placeholdersResolveChannelAndOnlineCount() {
        Config config = TestConfigs.config(
                "server.list.lobby.channels", List.of("login"),
                "channels.channel-list.login.display", "[Login Channel]");
        ChannelServiceImpl channels = new ChannelServiceImpl(config, servers(config));

        TestPlayers.online(proxy, "Alice", "lobby");
        TestPlayers.online(proxy, "Bob", "lobby");
        PlaceholderServiceImpl placeholders = new PlaceholderServiceImpl();
        channels.contributePlaceholders(placeholders);

        assertEquals("[Login Channel]", placeholders.resolve("{channel}",
                PlaceholderContext.builder().serverName("lobby").build()));
        assertEquals("", placeholders.resolve("{channel}",
                PlaceholderContext.builder().serverName("other").build()));
        assertEquals("2", placeholders.resolve("{login_channel_online}",
                PlaceholderContext.builder().serverName("lobby").build()));
        assertEquals("0", placeholders.resolve("{unknown_channel_online}",
                PlaceholderContext.builder().serverName("lobby").build()));
        assertEquals(2, channels.getChannel("login").orElseThrow().getOnlinePlayerCount());
    }

    @Test
    void configReloadRebuildsChannels() {
        Config initial = TestConfigs.config(
                "server.list.lobby.channels", List.of("login"),
                "channels.channel-list.login.display", "[Login]");
        ServerServiceImpl serverService = servers(initial);
        ChannelServiceImpl channels = new ChannelServiceImpl(initial, serverService);
        assertTrue(channels.getChannel("login").isPresent());

        Config reloaded = TestConfigs.config(
                "server.list.lobby.channels", List.of("games"),
                "channels.channel-list.games.display", "[Games]");
        KernelConfigUpdateEvent event = new KernelConfigUpdateEvent(reloaded);
        // In production both services subscribe to the same reload event.
        serverService.onEvent(event);
        channels.onEvent(event);

        assertTrue(channels.getChannel("login").isEmpty());
        assertEquals("[Games]", channels.getChannel("games").orElseThrow().getDisplayName());
        assertEquals("games", channels.getChannelForServer("lobby").orElseThrow().getName());
    }

    @Test
    void nullAndUnknownLookupsAreSafe() {
        ChannelServiceImpl channels = new ChannelServiceImpl(
                TestConfigs.config(), servers(TestConfigs.config()));

        assertTrue(channels.getChannel(null).isEmpty());
        assertTrue(channels.getChannelForServer(null).isEmpty());
        assertTrue(channels.getAllChannels().isEmpty());
    }
}
