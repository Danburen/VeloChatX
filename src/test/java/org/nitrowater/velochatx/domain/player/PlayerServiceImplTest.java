package org.nitrowater.velochatx.domain.player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.player.infra.PlayerBlockedMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerStatusMapper;
import org.nitrowater.velochatx.mapper.migration.V001__CreatePlayerTables;
import org.nitrowater.velochatx.mapper.migration.V002__CreateIgnoreRejectTables;
import org.nitrowater.velochatx.testsupport.FakePermissionService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.infra.io.database.H2DataBase;
import org.nitrowater.waterapi.domain.infra.io.database.migration.MigrationManager;
import org.nitrowater.waterapi.domain.model.UPlayer;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence-backed {@link PlayerServiceImpl} tests: first join / rename, attribution hydration,
 * block and chat-offline round trips, cache eviction and graceful DB-failure degradation.
 */
class PlayerServiceImplTest {

    private H2DataBase db;
    private PlayerMapper playerMapper;
    private PlayerStatusMapper statusMapper;
    private PlayerBlockedMapper blockedMapper;
    private PlayerServiceImpl service;
    private FakeWProxyServer proxy;

    private static H2DataBase freshDatabase() throws Exception {
        File dbFile = File.createTempFile("velochatx-player-service", ".db");
        assertTrue(dbFile.delete());
        H2DataBase database = new H2DataBase(dbFile);
        new MigrationManager(database, List.of(
                new V001__CreatePlayerTables(),
                new V002__CreateIgnoreRejectTables()
        )).migrate();
        return database;
    }

    @BeforeEach
    void setUp() throws Exception {
        db = freshDatabase();
        playerMapper = new PlayerMapper(db);
        statusMapper = new PlayerStatusMapper(db);
        blockedMapper = new PlayerBlockedMapper(db);
        service = new PlayerServiceImpl(new FakePermissionService(), playerMapper, statusMapper, blockedMapper);
        proxy = new FakeWProxyServer();
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private UPlayer player(String name) {
        return TestPlayers.create(proxy, name, "lobby", Locale.ENGLISH);
    }

    /** A player whose UUID is fixed but whose login name changed (name change on the platform). */
    private UPlayer renamed(UUID uuid, String newName) {
        return new UPlayer(
                new TestPlayers.NativePlayer(uuid, newName, Locale.ENGLISH, "lobby"),
                TestPlayers.STRATEGY, proxy);
    }

    @Test
    void firstJoinInsertsAndSubsequentJoinsReportExistingPlayer() {
        UPlayer alice = player("Alice");

        assertFalse(service.storeOrUpdatePlayerBaseInfo(alice));
        assertEquals("Alice", playerMapper.getPlayerName(alice.getUuid()));
        assertTrue(service.storeOrUpdatePlayerBaseInfo(alice));
    }

    @Test
    void renameIsPersistedAndReportedAsExisting() {
        UPlayer alice = player("Alice");
        service.storeOrUpdatePlayerBaseInfo(alice);

        assertTrue(service.storeOrUpdatePlayerBaseInfo(renamed(alice.getUuid(), "Alice2")));

        assertEquals("Alice2", playerMapper.getPlayerName(alice.getUuid()));
        assertTrue(playerMapper.getPlayerMap().containsKey("Alice2"));
        assertFalse(playerMapper.getPlayerMap().containsKey("Alice"));
    }

    @Test
    void attributionHydratesBlockedListAndChatOfflineFromTheDatabase() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        playerMapper.insertPlayerRecord(alice, "Alice");
        playerMapper.insertPlayerRecord(bob, "Bob");
        blockedMapper.addBlock(alice, bob);
        statusMapper.updateChatOffline(alice, true);

        PlayerAttribution attrs = service.getOrCreateAttrs(alice);

        assertTrue(attrs.isChatOffLine());
        assertTrue(attrs.isBlocked(bob));
        assertFalse(attrs.isBlocked(UUID.randomUUID()));
    }

    @Test
    void blockAndUnblockMutateCacheAndDatabase() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        playerMapper.insertPlayerRecord(alice, "Alice");
        playerMapper.insertPlayerRecord(bob, "Bob");

        service.blockPlayer(alice, bob);
        assertTrue(service.getOrCreateAttrs(alice).isBlocked(bob));
        assertTrue(blockedMapper.isBlocking(alice, bob));

        // Evict the cache: the next read must come from the database.
        service.removePlayer(alice);
        assertTrue(service.getPlayerAttribution(alice).isEmpty());
        assertTrue(service.getOrCreateAttrs(alice).isBlocked(bob));

        service.unblockPlayer(alice, bob);
        assertFalse(blockedMapper.isBlocking(alice, bob));
        service.removePlayer(alice);
        assertFalse(service.getOrCreateAttrs(alice).isBlocked(bob));
    }

    @Test
    void chatOfflineMutatesCacheAndDatabase() {
        UUID alice = UUID.randomUUID();
        playerMapper.insertPlayerRecord(alice, "Alice");

        service.setChatOffline(alice, true);
        assertTrue(service.getOrCreateAttrs(alice).isChatOffLine());
        assertTrue(statusMapper.isChatOffline(alice));

        service.removePlayer(alice);
        assertTrue(service.getOrCreateAttrs(alice).isChatOffLine());

        service.setChatOffline(alice, false);
        assertFalse(statusMapper.isChatOffline(alice));
    }

    @Test
    void leaveTimeIsPersisted() {
        UUID alice = UUID.randomUUID();
        playerMapper.insertPlayerRecord(alice, "Alice");

        service.updateLeaveTime(alice, 1_700_000_000_000L);

        assertNotNull(statusMapper.findById(alice.toString()).orElseThrow().getLeftTime());
    }

    @Test
    void onlineAttributionsExposeOnlyCachedEntries() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        PlayerAttribution attrs = service.getOrCreateAttrs(alice);

        assertEquals(Map.of(alice, attrs), service.getOnlinePlayerAttributions());
        assertFalse(service.getOnlinePlayerAttributions().containsKey(bob));
    }

    @Test
    void unknownPlayerAndClosedDatabaseDegradeToEmpty() {
        UPlayer alice = player("Alice");
        service.storeOrUpdatePlayerBaseInfo(alice);
        assertTrue(service.getPlayerName(UUID.randomUUID()).isEmpty());

        db.close();
        db = null;

        assertTrue(service.getPlayerName(alice.getUuid()).isEmpty());
    }

    @Test
    void migrationsAreIdempotent() {
        int executedAgain = new MigrationManager(db, List.of(
                new V001__CreatePlayerTables(),
                new V002__CreateIgnoreRejectTables()
        )).migrate();

        assertEquals(0, executedAgain);
        UPlayer alice = player("Alice");
        assertFalse(service.storeOrUpdatePlayerBaseInfo(alice));
        assertEquals("Alice", service.getPlayerName(alice.getUuid()).orElseThrow());
    }
}
