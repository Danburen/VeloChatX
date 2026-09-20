package org.nitrowater.velochatx.domain.server;

import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.service.ConfigurableService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ServerService}.
 * <p>
 * This service manages the server registry and provides display name
 * resolution based on configuration.
 * </p>
 *
 * <p>Configuration loaded from {@code config.yml} and can be reloaded
 * at runtime via {@link KernelConfigUpdateEvent}.</p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class ServerServiceImpl implements ServerService {

    private volatile Config config;
    private final Map<String, UServer> servers = new ConcurrentHashMap<>();
    private volatile List<String> boundServerNames = List.of();
    private final WProxyServer wProxyServer;

    /**
     * Create a new ServerServiceImpl.
     *
     * @param config       the config to read from
     * @param wProxyServer the proxy server
     */
    public ServerServiceImpl(Config config, WProxyServer wProxyServer) {
        this.config = config;
        this.wProxyServer = wProxyServer;
        loadServers();
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void onEvent(KernelConfigUpdateEvent event) {
        this.config = event.getConfig();
        loadServers();
    }

    @Override
    public List<UServer> getAllServers() {
        return List.copyOf(servers.values());
    }

    @Override
    public Optional<UServer> getServer(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(servers.get(name));
    }

    @Override
    public String getDisplay(String serverName) {
        if (serverName == null) {
            return "";
        }
        UServer server = servers.get(serverName);
        if (server != null) {
            return server.getDisplay();
        }
        return serverName;
    }

    @Override
    public int getServerCount() {
        return servers.size();
    }

    @Override
    public List<String> getBoundServerNames() {
        return boundServerNames;
    }

    @Override
    public int getTotalPlayerCount() {
        return wProxyServer.getAllOnlinePlayers().size();
    }

    @Override
    public UServer getOrCreate(String name) {
        return servers.computeIfAbsent(name, n -> new UServer(
                n,
                n,
                wProxyServer
        ));
    }

    private void loadServers() {
        servers.clear();
        // The raw display map is the admin's declared ("bound") server list; keep it even when
        // the display feature is off so the startup summary can report binding problems.
        Map<String, String> rawDisplayMap = getConfig().get(ServerConfigKey.DISPLAY_SERVER_DISPLAY);
        if (rawDisplayMap == null) {
            rawDisplayMap = Map.of();
        }
        boolean displayEnabled = getConfig().getBoolean(ServerConfigKey.DISPLAY_ENABLED);
        Map<String, String> displayMap = displayEnabled ? rawDisplayMap : Map.of();
        this.boundServerNames = List.copyOf(rawDisplayMap.keySet());
        // Always register every backend server; the display name falls back to the server name
        // when server-display is disabled or the server is not listed.
        for (UServer registered : wProxyServer.getAllRegisteredServers()) {
            String name = registered.getName();
            String display = displayMap.getOrDefault(name, name);
            servers.put(name, new UServer(name, display, wProxyServer));
        }
    }

    @Override
    public void contributePlaceholders(PlaceholderService service) {
        service.register("server", ctx -> ctx.getServerName()
                .map(this::getDisplay)
                .orElse(""));
        service.register("proxy", ctx ->
                getConfig().get(ServerConfigKey.PROXY_DISPLAY));
        service.register("online", ctx -> ctx.getServerName()
                .flatMap(this::getServer)
                .map(s -> String.valueOf(s.getPlayers().size()))
                .orElse("0"));
        service.register("total_online", ctx ->
                String.valueOf(getTotalPlayerCount()));
    }
}
