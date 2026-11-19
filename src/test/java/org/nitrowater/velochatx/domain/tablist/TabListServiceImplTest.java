package org.nitrowater.velochatx.domain.tablist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.testsupport.FakeLoggerService;
import org.nitrowater.velochatx.testsupport.FakeSchedulerService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.RecordingTabListProvider;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.utils.MessageParser;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards tab-list formatting, queue coalescing and the fixed / dynamic / event-driven
 * consumer modes.
 */
class TabListServiceImplTest {

    private FakeWProxyServer proxy;
    private PlaceholderServiceImpl placeholders;
    private RecordingTabListProvider provider;
    private FakeSchedulerService scheduler;
    private FakeLoggerService logger;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
        placeholders = new PlaceholderServiceImpl();
        placeholders.register("server", context -> context.getServerName().orElse(""));
        placeholders.register("player", context ->
                context.getPlayer().map(UPlayer::getLoginName).orElse(""));
        provider = new RecordingTabListProvider();
        scheduler = new FakeSchedulerService();
        logger = new FakeLoggerService();
    }

    private TabListServiceImpl service(Config config) {
        return new TabListServiceImpl(
                config, proxy, placeholders, provider, scheduler, logger);
    }

    private static Config enabled(Object... extraPairs) {
        Object[] pairs = new Object[extraPairs.length + 8];
        pairs[0] = "tab-list.enable";
        pairs[1] = true;
        pairs[2] = "tab-list.format";
        pairs[3] = "{player}@{server}";
        pairs[4] = "tab-list.header";
        pairs[5] = "H {server}";
        pairs[6] = "tab-list.footer";
        pairs[7] = "F {player}";
        System.arraycopy(extraPairs, 0, pairs, 8, extraPairs.length);
        return TestConfigs.config(pairs);
    }

    @Test
    void disabledServiceDoesNotTouchTheProvider() {
        TabListServiceImpl service = service(TestConfigs.config("tab-list.enable", false));
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "lobby");

        service.updateHeaderAndFooter(alice);
        service.updateTabListEntry(alice);
        service.addPlayerToTabList(alice, bob);
        service.removePlayerFromTabList(alice.getUuid(), bob);
        service.refreshAllTabLists();
        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), TabListUpdateTask.UpdateType.ENTRY, 1L));

        assertTrue(provider.headerFooters.isEmpty());
        assertTrue(provider.displayNameUpdates.isEmpty());
        assertTrue(provider.addedEntries.isEmpty());
        assertTrue(provider.removedEntries.isEmpty());
    }

    @Test
    void formatsHeaderFooterEntryAndEntryChanges() {
        TabListServiceImpl service = service(enabled());
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "lobby");

        service.updateHeaderAndFooter(alice);
        assertEquals(1, provider.headerFooters.size());
        assertEquals(MessageParser.parse("H lobby"), provider.headerFooters.get(0).header());
        assertEquals(MessageParser.parse("F Alice"), provider.headerFooters.get(0).footer());

        service.updateTabListEntry(alice);
        assertEquals(MessageParser.parse("Alice@lobby"),
                provider.displayNameUpdates.get(0).header());

        service.addPlayerToTabList(alice, bob);
        assertEquals(1, provider.addedEntries.size());
        assertEquals(alice.getUuid(), provider.addedEntries.get(0).source().getUuid());
        assertEquals(bob.getUuid(), provider.addedEntries.get(0).target().getUuid());

        service.removePlayerFromTabList(alice.getUuid(), bob);
        assertEquals(1, provider.removedEntries.size());
    }

    @Test
    void blankHeaderOrFooterSkipsSending() {
        TabListServiceImpl service = service(enabled(
                "tab-list.header", "",
                "tab-list.footer", "F {player}"));
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        service.updateHeaderAndFooter(alice);

        assertTrue(provider.headerFooters.isEmpty());
    }

    @Test
    void addThenRemoveForTheSamePairCoalescesToRemove() {
        TabListServiceImpl service = service(enabled(
                "tab-list.interval", 1000L,
                "tab-list.refresh-ratio", 0.0));
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "lobby");

        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), bob.getUuid(), TabListUpdateTask.UpdateType.ADD, 1L));
        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), bob.getUuid(), TabListUpdateTask.UpdateType.REMOVE, 2L));
        service.startConsumer();
        // Fixed mode schedules the consumer first, then the 5-minute full refresh.
        scheduler.tasks.get(0).run();

        assertTrue(provider.addedEntries.isEmpty());
        assertEquals(1, provider.removedEntries.size());
    }

    @Test
    void duplicateUpdatesForOnePlayerAreAppliedOncePerBatch() {
        TabListServiceImpl service = service(enabled(
                "tab-list.interval", 1000L,
                "tab-list.refresh-ratio", 0.0));
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer bob = TestPlayers.online(proxy, "Bob", "lobby");

        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), bob.getUuid(), TabListUpdateTask.UpdateType.ADD, 1L));
        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), bob.getUuid(), TabListUpdateTask.UpdateType.ADD, 2L));
        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), TabListUpdateTask.UpdateType.HEADER_FOOTER, 3L));
        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), TabListUpdateTask.UpdateType.HEADER_FOOTER, 4L));
        service.startConsumer();
        scheduler.tasks.get(0).run();

        assertEquals(1, provider.addedEntries.size());
        assertEquals(1, provider.headerFooters.size());
    }

    @Test
    void eventDrivenModeProcessesImmediatelyAndSchedulesOnlyFallbackRefresh() {
        TabListServiceImpl service = service(enabled(
                "tab-list.interval", 0L,
                "tab-list.refresh-ratio", 0.0));
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        service.startConsumer();

        assertEquals(1, scheduler.tasks.size());
        assertEquals(300, scheduler.tasks.get(0).delay());
        assertEquals(300, scheduler.tasks.get(0).period());
        assertEquals(TimeUnit.SECONDS, scheduler.tasks.get(0).timeUnit());

        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), TabListUpdateTask.UpdateType.ENTRY, 1L));
        assertEquals(1, provider.displayNameUpdates.size());

        service.stopConsumer();
        assertTrue(scheduler.tasks.get(0).isCancelled());
    }

    @Test
    void fixedIntervalModeSchedulesConsumerAndFullRefresh() {
        TabListServiceImpl service = service(enabled(
                "tab-list.interval", 1000L,
                "tab-list.refresh-ratio", 0.0));
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        service.startConsumer();

        assertEquals(2, scheduler.tasks.size());
        FakeSchedulerService.Task consumer = scheduler.tasks.get(0);
        assertEquals(0, consumer.delay());
        assertEquals(1000, consumer.period());
        assertEquals(TimeUnit.MILLISECONDS, consumer.timeUnit());
        assertEquals(300, scheduler.tasks.get(1).period());
        assertEquals(1000, service.getCurrentRefreshInterval());

        service.queueUpdate(new TabListUpdateTask(
                alice.getUuid(), TabListUpdateTask.UpdateType.ENTRY, 1L));
        assertTrue(provider.displayNameUpdates.isEmpty());
        consumer.run();
        assertEquals(1, provider.displayNameUpdates.size());
    }

    @Test
    void dynamicRatioScalesIntervalWithPlayerCount() {
        TabListServiceImpl service = service(enabled(
                "tab-list.interval", 1000L,
                "tab-list.refresh-ratio", 0.5));
        TestPlayers.online(proxy, "Alice", "lobby");
        TestPlayers.online(proxy, "Bob", "lobby");

        service.startConsumer();

        // 1000 * (1 + 2/20 * 0.5) = 1050
        assertEquals(1050, service.getCurrentRefreshInterval());
        assertEquals(3, scheduler.tasks.size());
        FakeSchedulerService.Task ratioTask = scheduler.tasks.get(1);
        assertEquals(10, ratioTask.delay());
        assertEquals(60, ratioTask.period());
        assertEquals(TimeUnit.SECONDS, ratioTask.timeUnit());

        for (int i = 0; i < 18; i++) {
            TestPlayers.online(proxy, "Extra" + i, "lobby");
        }
        ratioTask.run();

        // 1000 * (1 + 20/20 * 0.5) = 1500; the old consumer task is cancelled.
        assertEquals(1500, service.getCurrentRefreshInterval());
        assertTrue(scheduler.tasks.get(0).isCancelled());
    }

    @Test
    void removeTaskIsAppliedEvenWhenTheSourcePlayerIsAlreadyOffline() {
        TabListServiceImpl service = service(enabled(
                "tab-list.interval", 1000L,
                "tab-list.refresh-ratio", 0.0));
        UPlayer bob = TestPlayers.online(proxy, "Bob", "lobby");
        // A disconnecting player is gone from the proxy before the queued task runs.
        java.util.UUID offline = java.util.UUID.randomUUID();

        service.queueUpdate(new TabListUpdateTask(
                offline, bob.getUuid(), TabListUpdateTask.UpdateType.REMOVE, 1L));
        service.startConsumer();
        scheduler.tasks.get(0).run();

        assertEquals(1, provider.removedEntries.size());
        assertEquals(offline, provider.removedEntries.get(0).sourceUuid());
        assertEquals(bob.getUuid(), provider.removedEntries.get(0).target().getUuid());
    }

    @Test
    void fullRefreshCatchesProxyFailures() {
        TabListServiceImpl service = service(enabled());
        proxy.failOnlinePlayerLookup(true);

        assertDoesNotThrow(service::refreshAllTabLists);

        assertTrue(logger.hasWarningContaining("Tab list full refresh failed"));
    }

    @Test
    void configReloadRestartsARunningConsumer() {
        TabListServiceImpl service = service(enabled(
                "tab-list.interval", 0L,
                "tab-list.refresh-ratio", 0.0));
        service.startConsumer();
        assertFalse(scheduler.tasks.get(0).isCancelled());

        service.onEvent(new KernelConfigUpdateEvent(enabled(
                "tab-list.interval", 1000L,
                "tab-list.refresh-ratio", 0.0)));

        assertTrue(scheduler.tasks.get(0).isCancelled());
        // New consumer + new full refresh.
        assertEquals(3, scheduler.tasks.size());
        assertFalse(scheduler.tasks.get(1).isCancelled());
        assertEquals(1000, service.getCurrentRefreshInterval());
    }
}
