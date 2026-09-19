package org.nitrowater.velochatx.domain.player.infra;

import org.nitrowater.waterapi.domain.infra.io.database.BaseMapper;
import org.nitrowater.waterapi.domain.infra.io.database.Database;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Mapper for the player_blocked junction table.
 *
 * @since 2.1.0
 * @author Danburen
 */
public class PlayerBlockedMapper extends BaseMapper<PlayerBlocked, String> {

    public PlayerBlockedMapper(Database database) {
        super(database);
    }

    @Override
    public Optional<PlayerBlocked> findById(String id) {
        throw new UnsupportedOperationException("Use find(owner, target) for composite key lookup");
    }

    @Override
    public int insert(PlayerBlocked entity) {
        String sql = "INSERT INTO player_blocked (owner_uuid, target_uuid) VALUES (?, ?)";
        return executeUpdateWithCount(sql, new Object[]{entity.getOwnerUuid(), entity.getTargetUuid()});
    }

    @Override
    public int update(PlayerBlocked entity) {
        throw new UnsupportedOperationException("Junction table entries are immutable; delete and re-insert");
    }

    @Override
    public int delete(String id) {
        throw new UnsupportedOperationException("Use delete(owner, target) for composite key deletion");
    }

    // ======================== Business methods ========================

    /**
     * Check if owner blocks target.
     */
    public boolean isBlocking(UUID owner, UUID target) {
        String sql = "SELECT 1 FROM player_blocked WHERE owner_uuid = ? AND target_uuid = ? LIMIT 1";
        return executeQuery(sql, new Object[]{owner.toString(), target.toString()}, rs -> rs.next());
    }

    /**
     * Get all UUIDs that owner blocks.
     */
    public Set<UUID> getBlockedList(UUID owner) {
        String sql = "SELECT target_uuid FROM player_blocked WHERE owner_uuid = ?";
        return executeQuery(sql, new Object[]{owner.toString()}, rs -> {
            Set<UUID> result = new HashSet<>();
            while (rs.next()) {
                result.add(UUID.fromString(rs.getString("target_uuid")));
            }
            return result;
        });
    }

    /**
     * Add a block relationship.
     */
    public void addBlock(UUID owner, UUID target) {
        String sql = "MERGE INTO player_blocked (owner_uuid, target_uuid) KEY (owner_uuid, target_uuid) VALUES (?, ?)";
        executeUpdate(sql, new Object[]{owner.toString(), target.toString()});
    }

    /**
     * Remove a block relationship.
     */
    public void removeBlock(UUID owner, UUID target) {
        String sql = "DELETE FROM player_blocked WHERE owner_uuid = ? AND target_uuid = ?";
        executeUpdate(sql, new Object[]{owner.toString(), target.toString()});
    }

    /**
     * Remove all block relationships for an owner.
     */
    public void clearAll(UUID owner) {
        String sql = "DELETE FROM player_blocked WHERE owner_uuid = ?";
        executeUpdate(sql, new Object[]{owner.toString()});
    }
}
