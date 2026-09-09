package org.nitrowater.velochatx.domain.broadcast;

import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.shared.service.ConfigurableService;
import org.nitrowater.waterapi.domain.model.UPlayer;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for broadcast message management.
 * <p>
 * Handles timed broadcast messages, global and locale broadcasts,
 * as well as player join/leave entry broadcasts.
 * </p>
 *
 * <p>Configuration is accessed via {@link #getConfig()} with {@link BroadcastConfigKey}:</p>
 * <pre>{@code
 * // Check if global broadcast enabled
 * boolean global = broadcastService.getConfig().get(BroadcastConfigKey.GLOBAL_ENABLED);
 *
 * // Get global broadcast
 * Optional<BroadcastModel> globalBct = broadcastService.getGlobalBroadcast();
 * }</pre>
 *
 * @since 2.1.0
 * @author Danburen
 * @see BroadcastConfigKey
 * @see BroadcastModel
 */
public interface BroadcastService extends ConfigurableService<BroadcastConfigKey> {

    /**
     * Get the global broadcast model.
     *
     * @return an Optional containing the global broadcast, empty if disabled
     */
    Optional<BroadcastModel> getGlobalBroadcast();

    /**
     * Get all locale (local) broadcasts.
     *
     * @return immutable list of locale broadcasts
     */
    List<BroadcastModel> getLocaleBroadcasts();

    /**
     * Get a locale broadcast by name.
     *
     * @param name the broadcast name
     * @return an Optional containing the broadcast, empty if not found
     */
    Optional<BroadcastModel> getLocaleBroadcast(String name);

    /**
     * Get all broadcasts (global + locale) for a specific server.
     *
     * @param serverName the server name
     * @return list of broadcasts targeting this server
     */
    List<BroadcastModel> getBroadcastsForServer(String serverName);

    /**
     * Get broadcast messages for a specific server.
     * <p>
     * Merges messages from all broadcasts (global + locale) targeting this server.
     * </p>
     *
     * @param serverName the server name
     * @return list of broadcast messages, or empty list if none
     */
    List<String> getMessages(String serverName);

    /**
     * Get the broadcast prefix for a specific server.
     *
     * @param serverName the server name
     * @return the prefix, or empty string if none
     */
    String getPrefix(String serverName);

    /**
     * Get the message count for a specific server.
     *
     * @param serverName the server name
     * @return message count
     */
    int getMessageCount(String serverName);

    /**
     * Broadcast player join event (switch server).
     * <p>
     * Handles the join broadcast based on the entry-broadcast configuration.
     * Supports hierarchical broadcasting:
     * <ul>
     *   <li>Level 1 (highest): Player switches channel</li>
     *   <li>Level 2 (lowest): Player joins backend server</li>
     * </ul>
     * Note: Proxy join broadcast is handled by {@link #broadcastProxyJoin(UPlayer)}.
     * </p>
     *
     * @param player         the player who joined
     * @param currentServer  the server the player joined
     * @param previousServer the server the player left (null if first join)
     */
    void broadcastPlayerJoin(UPlayer player, UServer currentServer, UServer previousServer);

    /**
     * Broadcast player leave event (switch server).
     * <p>
     * Handles the leave broadcast based on the entry-broadcast configuration.
     * Supports hierarchical broadcasting:
     * <ul>
     *   <li>Level 1 (highest): Player switches channel</li>
     *   <li>Level 2 (lowest): Player leaves backend server</li>
     * </ul>
     * Note: Proxy leave broadcast is handled by {@link #broadcastProxyLeave(UPlayer)}.
     * </p>
     *
     * @param player         the player who left
     * @param previousServer the server the player left
     */
    void broadcastPlayerLeave(UPlayer player, UServer previousServer);

    /**
     * Broadcast player proxy join event.
     * <p>
     * Broadcasts to all online players (no server scope).
     * Used when player first joins the proxy (LoginEvent).
     * </p>
     *
     * @param player the player who joined
     */
    void broadcastProxyJoin(UPlayer player);

    /**
     * Broadcast player proxy leave event.
     * <p>
     * Broadcasts to all online players (no server scope).
     * Used when player disconnects from proxy (DisconnectEvent).
     * </p>
     *
     * @param player the player who left
     */
    void broadcastProxyLeave(UPlayer player);
}
