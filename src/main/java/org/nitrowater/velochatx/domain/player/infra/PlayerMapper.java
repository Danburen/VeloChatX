package org.nitrowater.velochatx.domain.player.infra;

import org.nitrowater.waterapi.domain.infra.io.database.BaseMapper;
import org.nitrowater.waterapi.domain.infra.io.database.Database;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.LinkedHashMap;

/**
 * Mapper for the {@code player} table.
 * <p>
 * Handles CRUD operations for {@link Player} entities.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class PlayerMapper extends BaseMapper<Player, String> {

    public PlayerMapper(Database database) {
        super(database);
    }

    @Override
    public Optional<Player> findById(String uuid) {
        String sql = "SELECT uuid, name, is_deleted, player_ind FROM player WHERE uuid = ?";
        return executeQuery(sql, new Object[]{uuid}, rs -> {
            if (rs.next()) {
                return Optional.of(
                        Player.builder()
                                .uuid(rs.getString("uuid"))
                                .name(rs.getString("name"))
                                .deleted(rs.getInt("is_deleted") == 1)
                                .index(rs.getInt("player_ind"))
                                .build()
                );
            }
            return Optional.empty();
        });
    }

    @Override
    public int insert(Player player) {
        String sql = "INSERT INTO player (uuid, name) VALUES (?, ?)";
        return executeUpdateWithCount(sql, new Object[]{player.getUuid(), player.getName()});
    }

    @Override
    public int update(Player player) {
        String sql = "UPDATE player SET name = ? WHERE uuid = ?";
        return executeUpdateWithCount(sql, new Object[]{player.getName(), player.getUuid()});
    }

    @Override
    public int delete(String uuid) {
        String sql = "DELETE FROM player WHERE uuid = ?";
        return executeUpdateWithCount(sql, new Object[]{uuid});
    }

    // ======================== Player-specific methods ========================

    /**
     * Insert a new player record with its initial status row.
     * <p>
     * Blocked relationships live in the {@code player_blocked} junction table
     * (see {@link PlayerBlockedMapper}) and need no seed row.
     * </p>
     *
     * @param uuid the player UUID
     * @param name the player name
     */
    public void insertPlayerRecord(UUID uuid, String name) {
        // Insert into player table
        insert(Player.builder()
                .uuid(uuid.toString())
                .name(name)
                .build());

        // Insert into player_status with default values
        executeUpdate(
                "INSERT INTO player_status (uuid, first_join_time) VALUES (?, CURRENT_TIMESTAMP)",
                new Object[]{uuid.toString()}
        );
    }

    /**
     * Update player's display name.
     *
     * @param uuid    the player UUID
     * @param newName the new name
     */
    public void updatePlayerName(UUID uuid, String newName) {
        executeUpdate(
                "UPDATE player SET name = ? WHERE uuid = ?",
                new Object[]{newName, uuid.toString()}
        );
    }

    /**
     * Get player name by UUID.
     *
     * @param uuid the player UUID
     * @return player name, or null if not found
     */
    public String getPlayerName(UUID uuid) {
        String sql = "SELECT name FROM player WHERE uuid = ?";
        return executeQuery(sql, new Object[]{uuid.toString()}, rs -> {
            if (rs.next()) {
                return rs.getString("name");
            }
            return null;
        });
    }

    /**
     * Get all players as a name→uuid map.
     *
     * @return map of player names to UUIDs
     */
    public Map<String, String> getPlayerMap() {
        String sql = "SELECT uuid, name FROM player";
        return executeQuery(sql, null, rs -> {
            Map<String, String> map = new LinkedHashMap<>();
            while (rs.next()) {
                map.put(rs.getString("name"), rs.getString("uuid"));
            }
            return map;
        });
    }

}
