package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.broadcast.BroadcastEntryConfigMigration;
import org.nitrowater.velochatx.domain.broadcast.BroadcastServiceImpl;
import org.nitrowater.velochatx.domain.channel.ChannelServiceImpl;
import org.nitrowater.velochatx.domain.server.ServerService.BindingState;
import org.nitrowater.velochatx.domain.server.ServerServiceImpl;
import org.nitrowater.velochatx.domain.shared.ServerBindingConfigMigration;
import org.nitrowater.velochatx.testsupport.FakeColoredMessageSender;
import org.nitrowater.velochatx.testsupport.FakeLoggerService;
import org.nitrowater.velochatx.testsupport.FakeSchedulerService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.spi.PlayerActionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration guard: a config produced by the migration classes must be directly consumable by
 * the runtime services. The migration unit tests assert the data shape; these assert the
 * services actually see the migrated membership and messages.
 */
class ConfigMigrationServiceIntegrationTest {

    @Test
    void migratedBindingConfigDrivesServerAndChannelServices() {
        Map<String, Object> data = legacyBindingConfig();
        new ServerBindingConfigMigration().migrate(data);

        FakeWProxyServer proxy = new FakeWProxyServer();
        proxy.registerServer("lobby");
        proxy.registerServer("server1");
        Config config = TestConfigs.config(data);
        ServerServiceImpl servers = new ServerServiceImpl(config, proxy);
        ChannelServiceImpl channels = new ChannelServiceImpl(config, servers);

        assertFalse(servers.hasLegacyBindings());
        assertEquals("[Lobby]", servers.getDisplay("lobby"));
        assertEquals("[S1]", servers.getDisplay("server1"));
        assertEquals(Optional.of(BindingState.BOUND), servers.getBindingState("lobby"));
        assertTrue(servers.getBoundServerNames().containsAll(List.of("lobby", "server1")));

        assertEquals(List.of("lobby", "server1"),
                channels.getChannel("login").orElseThrow().getServers().stream()
                        .map(org.nitrowater.waterapi.domain.model.UServer::getName)
                        .sorted()
                        .toList());
        assertEquals("login", channels.getChannelForServer("lobby").orElseThrow().getName());
        assertTrue(channels.canCommunicate("lobby", "server1"));
    }

    @Test
    void migratedEntryBroadcastConfigDrivesJoinLeaveBroadcasts() {
        Map<String, Object> data = legacyBroadcastConfig();
        new BroadcastEntryConfigMigration().migrate(data);

        FakeWProxyServer proxy = new FakeWProxyServer();
        proxy.registerServer("lobby");
        proxy.registerServer("server1");

        Config main = TestConfigs.config(
                "server.list.lobby.channels", List.of("login"),
                "server.list.server1.channels", List.of("login"),
                "channels.channel-list.login.display", "[Login]",
                "channels.global", false);
        ServerServiceImpl servers = new ServerServiceImpl(main, proxy);
        ChannelServiceImpl channels = new ChannelServiceImpl(main, servers);

        PlaceholderServiceImpl placeholders = new PlaceholderServiceImpl();
        placeholders.register("server", context -> context.getServerName().orElse(""));
        placeholders.register("player", context ->
                context.getPlayer().map(UPlayer::getLoginName).orElse(""));
        placeholders.register("group", context -> "");
        placeholders.register("prefix", context -> "");
        placeholders.register("suffix", context -> "");
        placeholders.register("proxy", context -> "[Proxy]");

        FakeColoredMessageSender sender = new FakeColoredMessageSender();
        BroadcastServiceImpl broadcasts = new BroadcastServiceImpl(
                null, null, servers, channels, proxy, placeholders,
                new PlayerActionService(sender, placeholders),
                new FakeSchedulerService(), new FakeLoggerService());
        broadcasts.updateConfig(TestConfigs.config(data));

        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        broadcasts.broadcastPlayerJoin(alice, servers.getServer("lobby").orElseThrow(),
                servers.getServer("server1").orElseThrow());
        broadcasts.broadcastPlayerLeave(alice, servers.getServer("server1").orElseThrow());

        assertEquals(List.of("§a(+)§rAlice 加入了 lobby"), sender.serverBroadcastTexts("lobby"));
        assertEquals(List.of("§c(-)§rAlice 离开了 server1"),
                sender.serverBroadcastTexts("server1"));
    }

    // ======================== Legacy config fixtures ========================

    private static Map<String, Object> legacyBindingConfig() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> display = new LinkedHashMap<>();
        display.put("lobby", "[Lobby]");
        display.put("server1", "[S1]");
        Map<String, Object> serverDisplay = new LinkedHashMap<>();
        serverDisplay.put("enable", true);
        serverDisplay.put("proxy", "[Proxy]");
        serverDisplay.put("display", display);
        data.put("server-display", serverDisplay);

        Map<String, Object> login = new LinkedHashMap<>();
        login.put("display", "[Login]");
        login.put("servers", new ArrayList<>(List.of("lobby", "server1")));
        Map<String, Object> channelList = new LinkedHashMap<>();
        channelList.put("login", login);
        Map<String, Object> channels = new LinkedHashMap<>();
        channels.put("channel-list", channelList);
        data.put("channels", channels);

        return data;
    }

    private static Map<String, Object> legacyBroadcastConfig() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("enable", true);
        server.put("log-to-console", false);
        server.put("send-to-all-subServer", false);
        server.put("broadcast-in-channel", true);
        server.put("join-prefix", "§a(+)§r");
        server.put("leave-prefix", "§c(-)§r");
        server.put("player-join-message", "{Group}{Prefix}{Player}{Suffix} 加入了 {Server}");
        server.put("player-leave-message", "{Group}{Prefix}{Player}{Suffix} 离开了 {Server}");
        data.put("join-leave-broadcast", server);

        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("enable", true);
        proxy.put("log-to-console", false);
        proxy.put("send-to-all-subServer", true);
        proxy.put("join-prefix", "§a[+]§r");
        proxy.put("leave-prefix", "§c[-]§r");
        proxy.put("player-join-message", "{Group}{Prefix}{Player}{Suffix} 加入了 {Proxy}");
        proxy.put("player-leave-message", "{Group}{Prefix}{Player}{Suffix} 离开了 {Proxy}");
        data.put("join-leave-proxy-broadcast", proxy);

        return data;
    }
}
