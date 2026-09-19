package org.nitrowater.velochatx.domain.player;

import org.nitrowater.waterapi.domain.model.UPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for managing player data and attributes.
 *
 * @since 2.1.0
 * @author Danburen
 * @see PlayerServiceImpl
 */
public interface PlayerService {

    /**
     * Store or update player record in database.
     *
     * @param player the player to store or update
     * @return {@code true} if player already existed, {@code false} if newly created
     */
    boolean storeOrUpdatePlayerBaseInfo(UPlayer player);

    /**
     * Get player's attribution (blocked list, chat offline status).
     *
     * @param uuid the player's UUID
     * @return player attribution, or empty if not found
     */
    Optional<PlayerAttribution> getPlayerAttribution(UUID uuid);

    /**
     * Update player's last leave time.
     *
     * @param uuid the player's UUID
     * @param time leave time in milliseconds
     */
    void updateLeaveTime(UUID uuid, long time);

    /**
     * Get all online players' attributions.
     *
     * @return map of UUID to attribution
     */
    Map<UUID, PlayerAttribution> getOnlinePlayerAttributions();

    /**
     * Get or create player attribution.
     *
     * @param uuid the player's UUID
     * @return player attribution
     */
    PlayerAttribution getOrCreateAttrs(UUID uuid);

    /**
     * Drop the cached attribution for a player (called on disconnect).
     * <p>The next {@link #getOrCreateAttrs(UUID)} reloads it from the database.</p>
     *
     * @param uuid the player's UUID
     */
    void removePlayer(UUID uuid);

    // === Block operations ===

    /**
     * Block a player.
     *
     * @param uuid       the player who blocks
     * @param targetUuid the player to block
     */
    void blockPlayer(UUID uuid, UUID targetUuid);

    /**
     * Unblock a player.
     *
     * @param uuid       the player who unblocks
     * @param targetUuid the player to unblock
     */
    void unblockPlayer(UUID uuid, UUID targetUuid);

    /**
     * Set player's chat offline status.
     *
     * @param uuid    the player's UUID
     * @param offline true to disable chat, false to enable
     */
    void setChatOffline(UUID uuid, boolean offline);
}
