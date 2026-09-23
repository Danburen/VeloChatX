package org.nitrowater.velochatx.domain.server;

import org.nitrowater.waterapi.domain.config.application.SchemaProvider;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholdersContributor;
import org.nitrowater.waterapi.domain.shared.service.ConfigurableService;
import org.nitrowater.waterapi.domain.shared.service.EventListener;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing backend servers connected to the proxy.
 * <p>
 * This service maintains the registry of all servers and provides methods
 * for server lookup, player tracking, and display name management.
 * </p>
 *
 * <p>Configuration accessed via {@link #getConfig()} with {@link ServerConfigKey}:</p>
 * <pre>{@code
 * // Check if display names enabled (dual-read: legacy server-display.enable then server.display-enable)
 * boolean enabled = ServerConfigKey.isDisplayEnabled(serverService.getConfig());
 *
 * // Get proxy display (dual-read: legacy server-display.proxy then server.proxy)
 * String proxy = serverService.getProxyDisplay();
 * }</pre>
 *
 * @since 2.1.0
 * @author Danburen
 * @see UServer
 * @see ServerConfigKey
 */
public interface ServerService extends SchemaProvider, ConfigurableService<ServerConfigKey>,
        EventListener<KernelConfigUpdateEvent>, PlaceholdersContributor {

    /**
     * Get all registered servers.
     *
     * @return immutable list of all servers
     */
    List<UServer> getAllServers();

    /**
     * Get a server by its name.
     *
     * @param name the server name (as configured in Velocity/Bukkit)
     * @return an Optional containing the server if found, empty otherwise
     */
    Optional<UServer> getServer(String name);

    /**
     * Get the display name for a server.
     *
     * @param serverName the server name
     * @return the display name, or the server name if not configured
     */
    String getDisplay(String serverName);

    /**
     * Effective proxy display: legacy {@code server-display.proxy} while present,
     * else {@code server.proxy}, else the ConfigKey default. Used by {@code {proxy}}
     * and console private-message rendering.
     *
     * @return the proxy display string
     */
    String getProxyDisplay();

    /**
     * Get the total number of registered servers.
     *
     * @return server count
     */
    int getServerCount();

    /**
     * Get the server names declared in {@code server-display.display}.
     * <p>
     * These are the names the admin bound display names to; the list may contain
     * names that are not actually registered in Velocity (typos / servers that are
     * not started yet). Use {@link #getServer(String)} to test existence.
     * </p>
     *
     * @return immutable list of bound server names, empty when none configured
     */
    List<String> getBoundServerNames();

    /**
     * Get the total number of online players across all servers.
     *
     * @return total player count
     */
    int getTotalPlayerCount();

    /**
     * Get a server or create by name and inner proxy server
     *
     * @param name  {@link  UServer#getName()} target server name
     * @return      {@link UServer} instance
     */
    UServer getOrCreate(String name);

    /** Binding state of a backend relative to the plugin's server declaration. */
    enum BindingState {
        /** Declared (legacy {@code server-display.display} or new {@code server.list}) and registered by Velocity. */
        BOUND,
        /** Registered by Velocity but not declared in config. */
        UNBOUND,
        /** Declared in config but not registered by Velocity. */
        PHANTOM
    }

    /**
     * Get the binding state of a server.
     *
     * @param serverName the server id (registered or declared)
     * @return the state, or empty when the name is neither registered nor declared
     */
    Optional<BindingState> getBindingState(String serverName);

    /**
     * Channel names a server declares via the new {@code server.list.<id>.channels} key.
     * <p>Only meaningful when legacy channel membership is absent; see
     * {@code docs/server-binding-plan.md}.</p>
     *
     * @param serverName the server id
     * @return immutable list of channel names (empty when none)
     */
    List<String> getDeclaredChannelNames(String serverName);

    /**
     * Whether any legacy binding key ({@code server-display.display}) is present in the config.
     *
     * @return {@code true} when legacy bindings are in use
     */
    boolean hasLegacyBindings();
}
