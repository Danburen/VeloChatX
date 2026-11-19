package org.nitrowater.velochatx.domain.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.broadcast.BroadcastServiceImpl;
import org.nitrowater.velochatx.domain.channel.ChannelServiceImpl;
import org.nitrowater.velochatx.domain.server.ServerServiceImpl;
import org.nitrowater.velochatx.testsupport.FakeColoredMessageSender;
import org.nitrowater.velochatx.testsupport.FakeLoggerService;
import org.nitrowater.velochatx.testsupport.FakeSchedulerService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.spi.PlayerActionService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the single status snapshot shared by {@code /velochatx status} and the startup
 * self-check: declared vs registered servers, binding states and broadcast counts.
 */
class StatusSnapshotTest {

    private FakeWProxyServer proxy;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
    }

    private StatusSnapshot snapshot(Config main, Config broadcast) {
        ServerServiceImpl servers = new ServerServiceImpl(main, proxy);
        ChannelServiceImpl channels = new ChannelServiceImpl(main, servers);
        PlaceholderServiceImpl placeholders = new PlaceholderServiceImpl();
        BroadcastServiceImpl broadcasts = new BroadcastServiceImpl(
                null, null, servers, channels, proxy, placeholders,
                new PlayerActionService(new FakeColoredMessageSender(), placeholders),
                new FakeSchedulerService(), new FakeLoggerService());
        broadcasts.updateConfig(broadcast);
        return StatusSnapshot.collect(servers, channels, broadcasts);
    }

    @Test
    void reportsDeclaredRegisteredAndUnboundServers() {
        proxy.registerServer("lobby");
        proxy.registerServer("survival");

        Config main = TestConfigs.config(
                "server.display-enable", true,
                "server.list.lobby.display", "[Lobby]",
                "server.list.lobby.channels", List.of("login"),
                "server.list.phantom.display", "[Phantom]",
                "channels.global", false,
                "channels.channel-list.login.display", "[Login]");

        Config broadcast = TestConfigs.config(
                "global.enable", true,
                "global.message-list", List.of("g1", "g2"),
                "locale.enable", true,
                "locale.broadcast-list.entry.enable", true,
                "locale.broadcast-list.entry.channel-list", List.of("login"),
                "locale.broadcast-list.entry.message-list", List.of("l1", "l2", "l3"));

        StatusSnapshot snapshot = snapshot(main, broadcast);

        assertEquals(2, snapshot.getServerBound());
        assertEquals(1, snapshot.getServerOnline());
        assertEquals(1, snapshot.getServerOffline());
        assertEquals(List.of("lobby"), snapshot.getOnlineServers());
        assertEquals(List.of("phantom"), snapshot.getOfflineServers());
        assertEquals(List.of("lobby", "survival"), snapshot.getRegisteredServers());
        assertEquals(List.of("survival"), snapshot.getUnboundServers());
        assertEquals(Map.of(
                "lobby", "BOUND",
                "phantom", "PHANTOM",
                "survival", "UNBOUND"), snapshot.getBindingStates());

        assertEquals(1, snapshot.getChannelCount());
        assertEquals(List.of("login"), snapshot.getChannelNames());
        assertEquals(1, snapshot.getBroadcastLocaleCount());
        assertEquals(2, snapshot.getBroadcastGlobalMessages());
        assertEquals(3, snapshot.getBroadcastLocaleMessages());
        assertEquals(5, snapshot.getBroadcastMessageTotal());
    }

    @Test
    void disabledBroadcastsAndEmptyRegistryProduceZeroes() {
        Config main = TestConfigs.config();
        Config broadcast = TestConfigs.config(
                "global.enable", false,
                "locale.enable", false);

        StatusSnapshot snapshot = snapshot(main, broadcast);

        assertEquals(0, snapshot.getServerBound());
        assertEquals(0, snapshot.getServerOnline());
        assertEquals(0, snapshot.getServerOffline());
        assertTrue(snapshot.getOnlineServers().isEmpty());
        assertTrue(snapshot.getOfflineServers().isEmpty());
        assertTrue(snapshot.getRegisteredServers().isEmpty());
        assertTrue(snapshot.getUnboundServers().isEmpty());
        assertTrue(snapshot.getBindingStates().isEmpty());
        assertEquals(0, snapshot.getChannelCount());
        assertEquals(0, snapshot.getBroadcastLocaleCount());
        assertEquals(0, snapshot.getBroadcastGlobalMessages());
        assertEquals(0, snapshot.getBroadcastLocaleMessages());
        assertEquals(0, snapshot.getBroadcastMessageTotal());
    }

    @Test
    void localeBroadcastWithoutMessagesCountsAsOneEntry() {
        Config main = TestConfigs.config();
        Config broadcast = TestConfigs.config(
                "global.enable", false,
                "locale.enable", true,
                "locale.broadcast-list.empty.enable", true,
                "locale.broadcast-list.empty.message-list", List.of());

        StatusSnapshot snapshot = snapshot(main, broadcast);

        assertEquals(1, snapshot.getBroadcastLocaleCount());
        assertEquals(0, snapshot.getBroadcastGlobalMessages());
        assertEquals(0, snapshot.getBroadcastLocaleMessages());
    }
}
