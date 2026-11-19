package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link WProxyServer} used by service tests.
 * <p>{@link UServer} requires a non-null proxy (it eagerly reads the player list), so every
 * server created here is backed by this instance.</p>
 */
public final class FakeWProxyServer implements WProxyServer {

    private final Map<String, UServer> servers = new LinkedHashMap<>();
    private final List<UPlayer> onlinePlayers = new CopyOnWriteArrayList<>();
    private volatile boolean failOnlinePlayerLookup;

    /** Register a backend server; its display defaults to its raw name. */
    public UServer registerServer(String name) {
        return registerServer(name, name);
    }

    /** Register (or replace) a backend server with an explicit display. */
    public UServer registerServer(String name, String display) {
        UServer server = new UServer(name, display, this);
        servers.put(name, server);
        return server;
    }

    public void unregisterServer(String name) {
        servers.remove(name);
    }

    public Optional<UServer> getServer(String name) {
        return Optional.ofNullable(servers.get(name));
    }

    /**
     * Resolve a server by name without recursing through {@link UPlayer#getCurrentServer()}.
     * <p>Registered servers are returned as-is. Unknown names get a detached view (empty player
     * list) so tests can model players moving to a backend before it is registered.</p>
     */
    public UServer serverView(String name) {
        UServer registered = servers.get(name);
        if (registered != null) {
            return registered;
        }
        return new UServer(name, name, DETACHED_PROXY);
    }

    /** A proxy whose server player lists are always empty; breaks the constructor recursion. */
    private static final WProxyServer DETACHED_PROXY = new WProxyServer() {
        @Override
        public List<UServer> getAllRegisteredServers() {
            return List.of();
        }

        @Override
        public boolean isServerRegistered(String name) {
            return false;
        }

        @Override
        public List<UPlayer> getServerCurrentUsers(String serverName) {
            return List.of();
        }

        @Override
        public List<UPlayer> getAllOnlinePlayers() {
            return List.of();
        }

        @Override
        public Optional<UPlayer> getPlayer(UUID playerUuid) {
            return Optional.empty();
        }

        @Override
        public Optional<UPlayer> getPlayer(String name) {
            return Optional.empty();
        }

        @Override
        public <T> T toNative() {
            return null;
        }
    };

    public void addPlayer(UPlayer player) {
        if (!onlinePlayers.contains(player)) {
            onlinePlayers.add(player);
        }
    }

    public void removePlayer(UPlayer player) {
        onlinePlayers.remove(player);
    }

    /** Make {@link #getAllOnlinePlayers()} throw, to exercise defensive refresh paths. */
    public void failOnlinePlayerLookup(boolean fail) {
        this.failOnlinePlayerLookup = fail;
    }

    @Override
    public List<UServer> getAllRegisteredServers() {
        return List.copyOf(servers.values());
    }

    @Override
    public boolean isServerRegistered(String name) {
        return servers.containsKey(name);
    }

    @Override
    public List<UPlayer> getServerCurrentUsers(String serverName) {
        List<UPlayer> result = new ArrayList<>();
        for (UPlayer player : onlinePlayers) {
            String current = player.getCurrentServer().map(UServer::getName).orElse("");
            if (current.equals(serverName)) {
                result.add(player);
            }
        }
        return result;
    }

    @Override
    public List<UPlayer> getAllOnlinePlayers() {
        if (failOnlinePlayerLookup) {
            throw new IllegalStateException("simulated proxy failure");
        }
        return List.copyOf(onlinePlayers);
    }

    @Override
    public Optional<UPlayer> getPlayer(UUID playerUuid) {
        return onlinePlayers.stream()
                .filter(player -> player.getUuid().equals(playerUuid))
                .findFirst();
    }

    @Override
    public Optional<UPlayer> getPlayer(String name) {
        return onlinePlayers.stream()
                .filter(player -> player.getLoginName().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public <T> T toNative() {
        @SuppressWarnings("unchecked")
        T self = (T) this;
        return self;
    }
}
