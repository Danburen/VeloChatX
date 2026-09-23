package org.nitrowater.velochatx.domain.server;

import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.config.schema.KeyItemSchema;
import org.nitrowater.waterapi.domain.config.schema.RegisteredSchema;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ServerService}.
 * <p>
 * This service manages the server registry and provides display name
 * resolution based on configuration.
 * </p>
 *
 * <p>Two declaration styles are supported and reconciled here:</p>
 * <ul>
 *   <li>legacy: {@code server-display.display.<id>} (display only);</li>
 *   <li>new: {@code server.list.<id>.{display,channels}}.</li>
 * </ul>
 * <p>Per-id the legacy display takes precedence; the declared set is the union of both. The
 * plugin still runs against Velocity's real backends, so mismatches degrade gracefully and are
 * surfaced by {@code BindingDiagnostics} instead of failing. See
 * {@code docs/server-binding-plan.md}.</p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class ServerServiceImpl implements ServerService {

    /**
     * config.yml {@code server.list}: each entry is a map with optional {@code display} and
     * {@code channels} leaves (both optional so a bare {@code <id>:{}} declaration is valid).
     * The legacy {@code server-display.display} freeform schema is intentionally not
     * registered: migration removes that subtree, and update must not re-add it.
     * Package-private so {@code ServerListSchemaTest} asserts against the real registration.
     */
    static final RegisteredSchema SERVER_LIST_SCHEMA =
            new RegisteredSchema(ServerConfigKey.SERVER_LIST,
                    KeyItemSchema.builder()
                            .optional("display")
                            .optional("channels")
                            .build());

    private volatile Config config;
    private final Map<String, UServer> servers = new ConcurrentHashMap<>();
    private volatile List<String> boundServerNames = List.of();
    private final Map<String, BindingState> bindingStates = new ConcurrentHashMap<>();
    private final Map<String, List<String>> declaredChannels = new ConcurrentHashMap<>();
    private volatile boolean legacyBindings = false;
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
    public List<RegisteredSchema> schemas() {
        return List.of(SERVER_LIST_SCHEMA);
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
    public Optional<BindingState> getBindingState(String serverName) {
        if (serverName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bindingStates.get(serverName));
    }

    @Override
    public List<String> getDeclaredChannelNames(String serverName) {
        if (serverName == null) {
            return List.of();
        }
        return declaredChannels.getOrDefault(serverName, List.of());
    }

    @Override
    public boolean hasLegacyBindings() {
        return legacyBindings;
    }

    @Override
    public int getTotalPlayerCount() {
        return wProxyServer.getAllOnlinePlayers().size();
    }

    @Override
    public UServer getOrCreate(String name) {
        return servers.computeIfAbsent(name, n -> new UServer(
                n,
                unboundDefaultDisplay(n, ServerConfigKey.isDisplayEnabled(getConfig())),
                wProxyServer
        ));
    }

    private void loadServers() {
        servers.clear();
        bindingStates.clear();
        declaredChannels.clear();

        // Legacy display map (server-display.display). Kept even when the display feature is off
        // so the startup summary can still report binding problems.
        Map<String, String> legacyDisplay = getConfig().get(ServerConfigKey.DISPLAY_SERVER_DISPLAY);
        if (legacyDisplay == null) {
            legacyDisplay = Map.of();
        }
        Map<String, Object> newList = getConfig().get(ServerConfigKey.SERVER_LIST);
        if (newList == null) {
            newList = Map.of();
        }
        boolean displayEnabled = ServerConfigKey.isDisplayEnabled(getConfig());
        this.legacyBindings = !legacyDisplay.isEmpty();

        // Declared set = legacy display keys (old) union new server.list keys.
        Set<String> declared = new LinkedHashSet<>();
        declared.addAll(legacyDisplay.keySet());
        declared.addAll(newList.keySet());
        this.boundServerNames = List.copyOf(declared);

        // Effective display: legacy per-id first, then new server.list.<id>.display.
        Map<String, String> effectiveDisplay = new LinkedHashMap<>();
        for (String id : declared) {
            String display = resolveDisplay(legacyDisplay, newList, id);
            if (display != null) {
                effectiveDisplay.put(id, display);
            }
            declaredChannels.put(id, channelsOf(newList, id));
        }

        // Always register every backend server. Declared ids use the configured display;
        // unbound backends fall back to the raw name — when the display feature is on, that
        // default is wrapped as [name] so it matches the bracketed style of configured displays
        // (see docs/server-binding-plan.md). When the feature is off, the raw name is intentional.
        Map<String, String> displayMap = displayEnabled ? effectiveDisplay : Map.of();
        for (UServer registered : wProxyServer.getAllRegisteredServers()) {
            String name = registered.getName();
            String display = displayMap.get(name);
            if (display == null) {
                display = unboundDefaultDisplay(name, displayEnabled);
            }
            servers.put(name, new UServer(name, display, wProxyServer));
        }

        // Binding state: declared+registered = BOUND, registered-only = UNBOUND, declared-only = PHANTOM.
        for (String id : declared) {
            bindingStates.put(id, servers.containsKey(id) ? BindingState.BOUND : BindingState.PHANTOM);
        }
        for (String id : servers.keySet()) {
            bindingStates.putIfAbsent(id, BindingState.UNBOUND);
        }
    }

    /**
     * Effective display name for a server: legacy per-id first, then
     * {@code server.list.<id>.display}.
     *
     * @param legacyDisplay {@code server-display.display} (never {@code null} after load)
     * @param newList       {@code server.list} (never {@code null} after load)
     * @param id            the server id
     * @return the declared display, or {@code null} when neither source declares one
     */
    static String resolveDisplay(Map<String, String> legacyDisplay,
                                 Map<String, Object> newList, String id) {
        if (legacyDisplay != null && legacyDisplay.get(id) != null) {
            return legacyDisplay.get(id);
        }
        Map<?, ?> entry = listEntry(newList, id);
        if (entry == null) {
            return null;
        }
        Object display = entry.get("display");
        return display == null ? null : display.toString();
    }

    /**
     * Read {@code server.list.<id>.channels} as strings.
     *
     * @param newList {@code server.list} (may be {@code null})
     * @param id      the server id
     * @return immutable list of channel names (empty when absent)
     */
    static List<String> channelsOf(Map<String, Object> newList, String id) {
        Map<?, ?> entry = listEntry(newList, id);
        if (entry == null) {
            return List.of();
        }
        Object channels = entry.get("channels");
        if (!(channels instanceof List<?> raw)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object channel : raw) {
            if (channel != null) {
                out.add(channel.toString());
            }
        }
        return out;
    }

    private static Map<?, ?> listEntry(Map<String, Object> list, String id) {
        if (list == null || id == null) {
            return null;
        }
        Object entry = list.get(id);
        return entry instanceof Map<?, ?> map ? map : null;
    }

    /**
     * Display for a backend that has no configured entry.
     * <p>When the display feature is enabled, the raw id is wrapped as {@code [id]} unless it
     * already carries {@code []}/{@code 【】} decoration, so unbound servers visually match
     * configured displays. When the feature is disabled, the raw id is kept as-is.</p>
     *
     * @param name           the Velocity server id
     * @param displayEnabled effective {@code server.display-enable} (legacy dual-read)
     * @return the default display string
     */
    static String unboundDefaultDisplay(String name, boolean displayEnabled) {
        if (!displayEnabled || name == null || name.isEmpty() || hasBracketDecoration(name)) {
            return name;
        }
        return "[" + name + "]";
    }

    /**
     * Whether the value is already wrapped in {@code []} or {@code 【】} (ASCII or fullwidth).
     * Color codes are not stripped here: this is only applied to raw Velocity server ids on the
     * unbound-default path, which never contain color codes.
     *
     * @param value the display candidate (maybe {@code null})
     * @return {@code true} when already bracket-wrapped
     */
    static boolean hasBracketDecoration(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return (value.startsWith("[") && value.endsWith("]"))
                || (value.startsWith("【") && value.endsWith("】"));
    }

    @Override
    public String getProxyDisplay() {
        var data = getConfig().getConfigData();
        return data.getResolvedLegacyValue(
                ServerConfigKey.PROXY_DISPLAY.getKey(),
                ServerConfigKey.SERVER_PROXY.getKey(),
                "[§5Proxy Server§r]"
        );
    }

    @Override
    public void contributePlaceholders(PlaceholderService service) {
        service.register("server", ctx -> ctx.getServerName()
                .map(this::getDisplay)
                .orElse(""));
        service.register("proxy", ctx -> getProxyDisplay());
        service.register("online", ctx -> ctx.getServerName()
                .flatMap(this::getServer)
                .map(s -> String.valueOf(s.getPlayers().size()))
                .orElse("0"));
        service.register("total_online", ctx ->
                String.valueOf(getTotalPlayerCount()));
    }
}
