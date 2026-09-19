package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.player.infra.PlayerBlockedMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerStatusMapper;
import org.nitrowater.velochatx.mapper.migration.V001__CreatePlayerTables;
import org.nitrowater.velochatx.mapper.migration.V002__CreateIgnoreRejectTables;
import org.nitrowater.waterapi.domain.infra.io.database.H2DataBase;
import org.nitrowater.waterapi.domain.infra.io.database.migration.MigrationManager;

import java.io.File;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end persistence test: migrations + player/status/blocked mappers.
 * <p>
 * Guards the fix where {@code insertPlayerRecord} still wrote to the
 * {@code player_ban_list} table that migration V002 drops.
 * </p>
 */
class PlayerPersistenceTest {

    private static H2DataBase freshDatabase() throws Exception {
        File dbFile = File.createTempFile("velochatx-test", ".db");
        assertTrue(dbFile.delete());
        H2DataBase db = new H2DataBase(dbFile);
        new MigrationManager(db, List.of(
                new V001__CreatePlayerTables(),
                new V002__CreateIgnoreRejectTables()
        )).migrate();
        return db;
    }

    @Test
    void firstJoinAndBlockedRelationsWorkEndToEnd() throws Exception {
        H2DataBase db = freshDatabase();
        try {
            PlayerMapper playerMapper = new PlayerMapper(db);
            PlayerStatusMapper statusMapper = new PlayerStatusMapper(db);
            PlayerBlockedMapper blockedMapper = new PlayerBlockedMapper(db);

            UUID alice = UUID.randomUUID();
            UUID bob = UUID.randomUUID();

            // First join must insert player + status only (player_ban_list no longer exists).
            assertDoesNotThrow(() -> playerMapper.insertPlayerRecord(alice, "Alice"));
            playerMapper.insertPlayerRecord(bob, "Bob");

            assertEquals("Alice", playerMapper.getPlayerName(alice));
            playerMapper.updatePlayerName(alice, "Alice2");
            assertEquals("Alice2", playerMapper.getPlayerName(alice));
            assertTrue(playerMapper.getPlayerMap().containsKey("Alice2"));

            // Status
            statusMapper.updateChatOffline(alice, true);
            assertTrue(statusMapper.isChatOffline(alice));
            statusMapper.updateLeftTime(alice, new Timestamp(System.currentTimeMillis()));
            statusMapper.updateChatOffline(alice, false);
            assertFalse(statusMapper.isChatOffline(alice));

            // Blocked junction (MERGE must be idempotent)
            blockedMapper.addBlock(alice, bob);
            blockedMapper.addBlock(alice, bob);
            assertTrue(blockedMapper.isBlocking(alice, bob));
            assertEquals(Set.of(bob), blockedMapper.getBlockedList(alice));
            assertFalse(blockedMapper.isBlocking(bob, alice));

            blockedMapper.removeBlock(alice, bob);
            assertFalse(blockedMapper.isBlocking(alice, bob));
        } finally {
            db.close();
        }
    }
}
