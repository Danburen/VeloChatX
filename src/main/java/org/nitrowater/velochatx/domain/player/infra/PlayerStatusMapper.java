package org.nitrowater.velochatx.domain.player.infra;

import org.nitrowater.waterapi.domain.infra.io.database.BaseMapper;
import org.nitrowater.waterapi.domain.infra.io.database.Database;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

/**
 * Mapper for the {@code player_status} table.
 * <p>
 * Handles CRUD operations for {@link PlayerStatus} entities.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class PlayerStatusMapper extends BaseMapper<PlayerStatus, String> {

    public PlayerStatusMapper(Database database) {
        super(database);
    }

    @Override
    public Optional<PlayerStatus> findById(String uuid) {
        String sql = "SELECT uuid, is_chat_offline, first_join_time, left_time FROM player_status WHERE uuid = ?";
        return executeQuery(sql, new Object[]{uuid}, rs -> {
            if (rs.next()) {
                return Optional.of(mapRow(rs));
            }
            return Optional.empty();
        });
    }

    @Override
    public int insert(PlayerStatus entity) {
        String sql = "INSERT INTO player_status (uuid, is_chat_offline, first_join_time, left_time) VALUES (?, ?, ?, ?)";
        return executeUpdateWithCount(sql, new Object[]{
                entity.getUuid(),
                entity.isChatOffLine() ? 1 : 0,
                entity.getFirstJoinTime(),
                entity.getLeftTime()
        });
    }

    @Override
    public int update(PlayerStatus entity) {
        String sql = "UPDATE player_status SET is_chat_offline = ?, first_join_time = ?, left_time = ? WHERE uuid = ?";
        return executeUpdateWithCount(sql, new Object[]{
                entity.isChatOffLine() ? 1 : 0,
                entity.getFirstJoinTime(),
                entity.getLeftTime(),
                entity.getUuid()
        });
    }

    @Override
    public int delete(String uuid) {
        String sql = "DELETE FROM player_status WHERE uuid = ?";
        return executeUpdateWithCount(sql, new Object[]{uuid});
    }

    // ======================== PlayerStatus-specific methods ========================

    /**
     * Update the chat offline status.
     *
     * @param uuid    the player UUID
     * @param offline whether chat offline is enabled
     */
    public void updateChatOffline(UUID uuid, boolean offline) {
        executeUpdate(
                "UPDATE player_status SET is_chat_offline = ? WHERE uuid = ?",
                new Object[]{offline ? 1 : 0, uuid.toString()}
        );
    }

    /**
     * Update the player's first join time.
     *
     * @param uuid          the player UUID
     * @param firstJoinTime the join timestamp
     */
    public void updateFirstJoinTime(UUID uuid, Timestamp firstJoinTime) {
        executeUpdate(
                "UPDATE player_status SET first_join_time = ? WHERE uuid = ?",
                new Object[]{firstJoinTime, uuid.toString()}
        );
    }

    /**
     * Update the player's last leave time.
     *
     * @param uuid     the player UUID
     * @param leftTime the leave timestamp
     */
    public void updateLeftTime(UUID uuid, Timestamp leftTime) {
        executeUpdate(
                "UPDATE player_status SET left_time = ? WHERE uuid = ?",
                new Object[]{leftTime, uuid.toString()}
        );
    }

    /**
     * Check if player has chat offline mode enabled.
     *
     * @param uuid the player UUID
     * @return true if chat offline is enabled
     */
    public boolean isChatOffline(UUID uuid) {
        String sql = "SELECT is_chat_offline FROM player_status WHERE uuid = ?";
        return executeQuery(sql, new Object[]{uuid.toString()}, rs -> {
            if (rs.next()) {
                return rs.getInt("is_chat_offline") == 1;
            }
            return false;
        });
    }

    // ======================== Mapping ========================

    private PlayerStatus mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return PlayerStatus.builder()
                .uuid(rs.getString("uuid"))
                .chatOffLine(rs.getInt("is_chat_offline") == 1)
                .firstJoinTime(rs.getTimestamp("first_join_time"))
                .leftTime(rs.getTimestamp("left_time"))
                .build();
    }
}
