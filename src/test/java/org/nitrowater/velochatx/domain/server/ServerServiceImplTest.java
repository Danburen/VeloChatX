package org.nitrowater.velochatx.domain.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.server.ServerService.BindingState;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the legacy/new binding reconciliation performed by {@link ServerServiceImpl}:
 * declared ∪ registered, display precedence, and BOUND / UNBOUND / PHANTOM states.
 */
class ServerServiceImplTest {

    private FakeWProxyServer proxy;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
    }

    @Test
    void reconcilesLegacyAndNewBindings() {
        UServer lobby = proxy.registerServer("lobby");
        UServer skyblock = proxy.registerServer("skyblock");
        UServer survival = proxy.registerServer("survival");

        Config config = TestConfigs.config(
                "server.display-enable", true,
                "server-display.display", Map.of("lobby", "[Legacy]", "old", "[Old]"),
                "server.list", Map.of(
                        "lobby", Map.of("display", "[New]"),
                        "skyblock", Map.of("display", "[Sky]"),
                        "ghost", Map.of("display", "[Ghost]")));

        ServerServiceImpl service = new ServerServiceImpl(config, proxy);

        // Declared set = legacy ids ∪ new ids; ordering follows Map iteration, so compare as a set.
        assertEquals(Set.of("lobby", "old", "skyblock", "ghost"),
                new HashSet<>(service.getBoundServerNames()));
        assertEquals(3, service.getServerCount());
        assertTrue(service.hasLegacyBindings());

        // Legacy display wins per id; new display is the fallback.
        assertEquals("[Legacy]", service.getDisplay("lobby"));
        assertEquals("[Sky]", service.getDisplay("skyblock"));
        // Unbound backend: bracketed raw name because the display feature is on.
        assertEquals("[survival]", service.getDisplay("survival"));
        // Unknown name: raw pass-through.
        assertEquals("nope", service.getDisplay("nope"));

        assertEquals(Optional.of(BindingState.BOUND), service.getBindingState("lobby"));
        assertEquals(Optional.of(BindingState.BOUND), service.getBindingState("skyblock"));
        assertEquals(Optional.of(BindingState.PHANTOM), service.getBindingState("old"));
        assertEquals(Optional.of(BindingState.PHANTOM), service.getBindingState("ghost"));
        assertEquals(Optional.of(BindingState.UNBOUND), service.getBindingState("survival"));
        assertEquals(Optional.empty(), service.getBindingState("unknown"));

        // Registry entries are rebuilt from the proxy; UServer equality is by name.
        assertEquals(lobby, service.getServer("lobby").orElseThrow());
        assertEquals(skyblock, service.getServer("skyblock").orElseThrow());
        assertEquals(survival, service.getServer("survival").orElseThrow());
    }

    @Test
    void displayDisabledKeepsRawNamesAndSkipsBrackets() {
        proxy.registerServer("lobby");
        proxy.registerServer("survival");

        Config config = TestConfigs.config(
                "server.display-enable", false,
                "server.list.lobby.display", "[Configured]");

        ServerServiceImpl service = new ServerServiceImpl(config, proxy);

        assertEquals("lobby", service.getDisplay("lobby"));
        assertEquals("survival", service.getDisplay("survival"));
        assertEquals(Optional.of(BindingState.BOUND), service.getBindingState("lobby"));
        assertEquals(Optional.of(BindingState.UNBOUND), service.getBindingState("survival"));
        assertFalse(service.hasLegacyBindings());
    }

    @Test
    void getOrCreateRegistersAnUnknownBackend() {
        ServerServiceImpl service = new ServerServiceImpl(
                TestConfigs.config("server.display-enable", true), proxy);

        UServer created = service.getOrCreate("newserver");

        assertEquals("newserver", created.getName());
        assertEquals("[newserver]", created.getDisplay());
        assertTrue(service.getServer("newserver").isPresent());
        assertEquals(1, service.getServerCount());
        // getOrCreate registers the backend for lookups but is not a binding declaration.
        assertEquals(Optional.empty(), service.getBindingState("newserver"));
    }

    @Test
    void declaredChannelsAreExposedPerServer() {
        proxy.registerServer("lobby");
        Config config = TestConfigs.config(
                "server.list.lobby.channels", List.of("login", "games"),
                "server.list.other.channels", List.of());
        ServerServiceImpl service = new ServerServiceImpl(config, proxy);

        assertEquals(List.of("login", "games"), service.getDeclaredChannelNames("lobby"));
        assertEquals(List.of(), service.getDeclaredChannelNames("other"));
        assertEquals(List.of(), service.getDeclaredChannelNames("missing"));
        assertEquals(List.of(), service.getDeclaredChannelNames(null));
    }

    @Test
    void nullLookupsAreSafe() {
        ServerServiceImpl service = new ServerServiceImpl(TestConfigs.config(), proxy);

        assertEquals(Optional.empty(), service.getServer(null));
        assertEquals("", service.getDisplay(null));
        assertEquals(Optional.empty(), service.getBindingState(null));
    }

    @Test
    void placeholdersResolveDisplayProxyAndCounts() {
        proxy.registerServer("lobby");
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "lobby");
        proxy.registerServer("survival");

        ServerServiceImpl service = new ServerServiceImpl(TestConfigs.config(
                "server.display-enable", true,
                "server.proxy", "[Proxy]",
                "server.list.lobby.display", "[Lobby]"), proxy);

        PlaceholderServiceImpl placeholders = new PlaceholderServiceImpl();
        service.contributePlaceholders(placeholders);

        PlaceholderContext lobby = PlaceholderContext.builder().serverName("lobby").build();
        assertEquals("[Lobby]", placeholders.resolve("{server}", lobby));
        assertEquals("[Proxy]", placeholders.resolve("{proxy}", lobby));
        assertEquals("2", placeholders.resolve("{online}", lobby));
        assertEquals("2", placeholders.resolve("{total_online}", lobby));
        assertEquals("[survival]", placeholders.resolve("{server}",
                PlaceholderContext.builder().serverName("survival").build()));
        assertEquals("0", placeholders.resolve("{online}",
                PlaceholderContext.builder().serverName("unknown").build()));
        assertEquals("", placeholders.resolve("{server}", PlaceholderContext.builder().build()));
    }

    @Test
    void getTotalPlayerCountReadsTheProxy() {
        ServerServiceImpl service = new ServerServiceImpl(TestConfigs.config(), proxy);
        TestPlayers.online(proxy, "Alice", "lobby");
        TestPlayers.online(proxy, "Bob", "survival");

        assertEquals(2, service.getTotalPlayerCount());
    }

    @Test
    void configReloadRebuildsTheRegistry() {
        proxy.registerServer("lobby");
        proxy.registerServer("skyblock");
        ServerServiceImpl service = new ServerServiceImpl(TestConfigs.config(
                "server.list.lobby.display", "[Lobby]"), proxy);
        assertTrue(service.getServer("lobby").isPresent());
        assertTrue(service.getServer("skyblock").isPresent());
        assertEquals(List.of("lobby"), service.getBoundServerNames());

        service.onEvent(new KernelConfigUpdateEvent(TestConfigs.config(
                "server.display-enable", true,
                "server.list.skyblock.display", "[Sky]")));

        assertTrue(service.getServer("lobby").isPresent());
        assertEquals("[Sky]", service.getDisplay("skyblock"));
        assertEquals(List.of("skyblock"), service.getBoundServerNames());
        assertFalse(service.hasLegacyBindings());
    }
}
