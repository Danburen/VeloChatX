package org.nitrowater.velochatx.domain.tablist;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.service.SchedulerService;
import org.nitrowater.waterapi.domain.spi.TabListProvider;
import org.nitrowater.waterapi.utils.MessageParser;
import org.nitrowater.waterapi.utils.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link TabListService}.
 * <p>
 * This service handles tab list header, footer, and player entry formatting
 * using the platform-agnostic {@link TabListProvider} interface.
 * </p>
 * <p>
 * Supports three modes of operation:
 * <ul>
 *   <li>Event-driven (interval=0): Updates are processed immediately, with periodic full refresh as fallback</li>
 *   <li>Fixed frequency (interval>0, ratio=0): Updates are processed at a fixed interval</li>
 *   <li>Dynamic frequency (interval>0, ratio>0): Refresh interval adjusts based on player count</li>
 * </ul>
 * </p>
 *
 * <p>
 * We use producer-consumer model to process tab-list update to decrease the number of updates sent to the client.
 * The update queue is a concurrent queue that stores the update tasks.
 * The consumer task processes the queue at a configured interval, applying only the latest update for each player
 * to avoid redundant updates.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class TabListServiceImpl implements TabListService {

    private volatile Config config;
    private final WProxyServer wProxyServer;
    private final PlaceholderService placeholderService;
    private final TabListProvider tabListProvider;
    private final SchedulerService scheduler;
    private final LoggerService loggerService;

    private final Queue<TabListUpdateTask> updateQueue = new ConcurrentLinkedQueue<>();
    private volatile SchedulerService.TaskHandle consumerHandle;
    private volatile SchedulerService.TaskHandle fullRefreshHandle;
    private volatile SchedulerService.TaskHandle ratioHandle;
    @Getter
    private volatile long currentRefreshInterval;
    private volatile boolean consumerRunning = false;

    /**
     * Create a new TabListServiceImpl.
     *
     * @param config             the config to read from
     * @param wProxyServer       the WProxyServer wrapper
     * @param placeholderService the placeholder service for template resolution
     * @param tabListProvider    the platform-specific TabList provider
     * @param scheduler          the scheduler service for task scheduling
     * @param loggerService      used to report a failed full refresh without killing its timer
     */
    public TabListServiceImpl(Config config, WProxyServer wProxyServer, PlaceholderService placeholderService,
                              TabListProvider tabListProvider, SchedulerService scheduler,
                              LoggerService loggerService) {
        this.config = config;
        this.wProxyServer = wProxyServer;
        this.placeholderService = placeholderService;
        this.tabListProvider = tabListProvider;
        this.scheduler = scheduler;
        this.loggerService = loggerService;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void onEvent(KernelConfigUpdateEvent event) {
        this.config = event.getConfig();
        // Restart consumer with new config
        if (consumerRunning) {
            stopConsumer();
            startConsumer();
        }
    }

    @Override
    public void updateHeaderAndFooter(UPlayer player) {
        if (!isEnabled()) return;
        String headerFormat = getConfig().get(TabListConfigKey.HEADER);
        String footerFormat = getConfig().get(TabListConfigKey.FOOTER);
        if (StringUtil.anyIsBlank(headerFormat, footerFormat)) return;

        PlaceholderContext context = buildContext(player);
        // MessageParser (not Component.text) so that § color codes in the format are applied.
        Component header = MessageParser.parse(placeholderService.resolve(headerFormat, context));
        Component footer = MessageParser.parse(placeholderService.resolve(footerFormat, context));
        tabListProvider.sendHeaderAndFooter(player, header, footer);
    }

    @Override
    public void updateTabListEntry(UPlayer player) {
        if (!isEnabled()) return;

        String format = getConfig().get(TabListConfigKey.FORMAT);
        PlaceholderContext context = buildContext(player);

        Component displayName = MessageParser.parse(placeholderService.resolve(format, context));

        tabListProvider.updateDisplayName(player, displayName);
    }

    @Override
    public void addPlayerToTabList(UPlayer sourcePlayer, UPlayer targetPlayer) {
        if (!isEnabled()) return;

        String format = getConfig().get(TabListConfigKey.FORMAT);
        PlaceholderContext context = buildContext(sourcePlayer);

        Component displayName = MessageParser.parse(placeholderService.resolve(format, context));

        tabListProvider.addEntry(sourcePlayer, targetPlayer, displayName);
    }

    /**
     * Build a placeholder context that also carries the player's current server name
     * so that {@code {server}} resolves in tab list formats.
     */
    private PlaceholderContext buildContext(UPlayer player) {
        return PlaceholderContext.builder()
                .player(player)
                .serverName(player.getCurrentServer().map(UServer::getName).orElse(null))
                .build();
    }

    @Override
    public void removePlayerFromTabList(UUID sourceUuid, UPlayer targetPlayer) {
        if (!isEnabled()) return;

        tabListProvider.removeEntry(sourceUuid, targetPlayer);
    }

    @Override
    public void refreshAllTabLists() {
        if (!isEnabled()) return;
        // A scheduled task that throws is cancelled permanently: one broken entry must not
        // kill the 5-minute full refresh.
        try {
            wProxyServer.getAllOnlinePlayers().forEach(player -> {
                updateHeaderAndFooter(player);
                updateTabListEntry(player);
            });
        } catch (RuntimeException e) {
            loggerService.warning("Tab list full refresh failed, skipped this round: " + e);
        }
    }
    @Override
    public void queueUpdate(TabListUpdateTask task) {
        // offsetting the opposite operation and retaining only the final state
        UUID playerUuid = task.getPlayerUuid();
        if (task.getType() == TabListUpdateTask.UpdateType.REMOVE) {
            updateQueue.removeIf(t ->
                samePair(t, task) &&
                t.getType() == TabListUpdateTask.UpdateType.ADD
            );
        } else if (task.getType() == TabListUpdateTask.UpdateType.ADD) {
            updateQueue.removeIf(t ->
                samePair(t, task) &&
                t.getType() == TabListUpdateTask.UpdateType.REMOVE
            );
        } else {
            updateQueue.removeIf(t ->
                t.getPlayerUuid().equals(playerUuid) &&
                t.getType() == task.getType()
            );
        }
        updateQueue.offer(task);
        // In event-driven mode (interval=0), process immediately
        long interval = getConfig().get(TabListConfigKey.INTERVAL);
        if (interval == 0) {
            processUpdates();
        }
    }

    /**
     * Whether two ADD/REMOVE tasks target the same (player, target) pair.
     */
    private static boolean samePair(TabListUpdateTask a, TabListUpdateTask b) {
        return a.getPlayerUuid().equals(b.getPlayerUuid())
                && Objects.equals(a.getTargetPlayerUuid(), b.getTargetPlayerUuid());
    }

    @Override
    public void startConsumer() {
        if (consumerRunning) return;
        long interval = getConfig().get(TabListConfigKey.INTERVAL);
        double configuredRatio = getConfig().get(TabListConfigKey.REFRESH_RATIO);
        double ratio = Math.max(0.0, Math.min(1.0, configuredRatio));
        if (interval == 0) {
            // event-driven mode: only start periodic full refresh as fallback
            startFullRefreshTask();
            consumerRunning = true;
            return;
        }
        // start consumer task
        consumerHandle = adjustRefreshInterval(interval, ratio);
        if (ratio > 0) {
            ratioHandle = scheduler.scheduleAtFixedRate(
                    () -> adjustRefreshInterval(interval, ratio),
                    10,
                    60,
                    TimeUnit.SECONDS);
        }
        startFullRefreshTask();
        consumerRunning = true;
    }

    @Override
    public void stopConsumer() {
        if (!consumerRunning) return;
        if (consumerHandle != null) {
            consumerHandle.cancel();
            consumerHandle = null;
        }
        if (fullRefreshHandle != null) {
            fullRefreshHandle.cancel();
            fullRefreshHandle = null;
        }
        if (ratioHandle != null) {
            ratioHandle.cancel();
            ratioHandle = null;
        }
        updateQueue.clear();
        consumerRunning = false;
    }

    /**
     * Adjust the refresh interval based on current player count.
     *
     * @param baseInterval the base interval from config
     * @param ratio        the dynamic ratio from config
     */
    private SchedulerService.TaskHandle adjustRefreshInterval(long baseInterval, double ratio) {
        int playerCount = wProxyServer.getAllOnlinePlayers().size();
        long newInterval = (long) (baseInterval * (1 + (playerCount / 20.0) * ratio));
        // consumerHandle == null covers a reload: stopConsumer() left currentRefreshInterval
        // unchanged, so the interval alone would never trigger a reschedule.
        if (newInterval != currentRefreshInterval || consumerHandle == null) {
            currentRefreshInterval = newInterval;
            // Restart consumer with new interval
            if (consumerHandle != null) {
                consumerHandle.cancel();
            }
            consumerHandle = scheduler.scheduleAtFixedRate(
                    this::processUpdates,
                    0,
                    currentRefreshInterval,
                    TimeUnit.MILLISECONDS
            );
        }
        return consumerHandle;
    }

    /**
     * Start the periodic full refresh task (fallback for event-driven mode).
     */
    private void startFullRefreshTask() {
        // Full refresh every 5 minutes (300 seconds)
        fullRefreshHandle = scheduler.scheduleAtFixedRate(
                this::refreshAllTabLists,
                300,
                300,
                TimeUnit.SECONDS
        );
    }

    /**
     * Process all queued updates.
     */
    private void processUpdates() {
        if (updateQueue.isEmpty()) return;
        List<TabListUpdateTask> tasks = new ArrayList<>();
        for (TabListUpdateTask task = updateQueue.poll(); task != null; task = updateQueue.poll()) {
            tasks.add(task);
        }

        Map<String, TabListUpdateTask> latestTasks = new LinkedHashMap<>();
        for (TabListUpdateTask task : tasks) {
            String key = task.getType() + ":" + task.getPlayerUuid() + ":" + task.getTargetPlayerUuid();
            latestTasks.put(key, task);
        }
        // Apply updates
        latestTasks.values().forEach(task -> {
            // REMOVE must not depend on the source player still being online: the entry of a
            // player who just left the proxy has to disappear from everyone else's list.
            if (task.getType() == TabListUpdateTask.UpdateType.REMOVE) {
                if (task.getTargetPlayerUuid() != null) {
                    wProxyServer.getPlayer(task.getTargetPlayerUuid()).ifPresent(target ->
                            removePlayerFromTabList(task.getPlayerUuid(), target));
                }
                return;
            }
            wProxyServer.getPlayer(task.getPlayerUuid()).ifPresent(player -> {
                switch (task.getType()) {
                    case HEADER_FOOTER:
                        updateHeaderAndFooter(player);
                        break;
                    case ENTRY:
                        updateTabListEntry(player);
                        break;
                    case ADD:
                        if (task.getTargetPlayerUuid() != null) {
                            wProxyServer.getPlayer(task.getTargetPlayerUuid()).ifPresent(target -> {
                                addPlayerToTabList(player, target);
                            });
                        }
                        break;
                    default:
                        break;
                }
            });
        });
    }

    /**
     * Check if tab list is enabled in config.
     *
     * @return true if enabled
     */
    private boolean isEnabled() {
        return getConfig().get(TabListConfigKey.ENABLED);
    }
}
