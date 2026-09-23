package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.velochatx.domain.broadcast.BroadcastConfigKey;
import org.nitrowater.velochatx.domain.broadcast.BroadcastService;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.waterapi.domain.logger.LoggerService;

/**
 * Logs a one-shot self-check summary while the plugin is loading its servers,
 * channels and broadcasts.
 * <p>
 * These lines are diagnostic only: they are hard-coded in English (no i18n) and emitted
 * through {@link LoggerService#debug(String)}, so they stay hidden unless {@code debug: true}
 * is set in {@code config.yml}. The same numbers are exposed on demand to admins through
 * {@code /velochatx status}; both build on {@link StatusSnapshot} so the two never drift.
 * Call {@link #report()} once, right after the services have been wired on first load.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class LoadSummaryReporter {

    /** Inline the channel name list only while it still fits comfortably on one line. */
    private static final int MAX_INLINE_CHANNEL_NAMES_LENGTH = 48;

    private final ServerService serverService;
    private final ChannelService channelService;
    private final BroadcastService broadcastService;
    private final LoggerService loggerService;

    public LoadSummaryReporter(ServerService serverService,
                               ChannelService channelService,
                               BroadcastService broadcastService,
                               LoggerService loggerService) {
        this.serverService = serverService;
        this.channelService = channelService;
        this.broadcastService = broadcastService;
        this.loggerService = loggerService;
    }

    /**
     * Report the loaded servers, channels and broadcasts at debug level.
     * <p>No-op when the kernel has no logger service (degraded startup).</p>
     */
    public void report() {
        if (loggerService == null) {
            return;
        }
        StatusSnapshot snapshot = StatusSnapshot.collect(serverService, channelService, broadcastService);
        reportServers(snapshot);
        reportChannels(snapshot);
        reportBroadcasts(snapshot);
    }

    /**
     * Report the servers declared in {@code server-display.display}.
     * <p>Nothing is logged when no server is declared.</p>
     */
    private void reportServers(StatusSnapshot snapshot) {
        if (snapshot.getServerBound() == 0) {
            return;
        }
        if (snapshot.getServerOffline() == 0) {
            loggerService.debug("Servers: {0} bound, all online.", snapshot.getServerBound());
        } else if (snapshot.getServerOnline() == 0) {
            loggerService.debug("Servers: {0} bound, none online yet: {1}.",
                    snapshot.getServerBound(), String.join(", ", snapshot.getOfflineServers()));
        } else {
            loggerService.debug("Servers: {0} bound, online: {1}; offline: {2}.",
                    snapshot.getServerBound(),
                    String.join(", ", snapshot.getOnlineServers()),
                    String.join(", ", snapshot.getOfflineServers()));
        }
    }

    private void reportChannels(StatusSnapshot snapshot) {
        int count = snapshot.getChannelCount();
        String names = String.join(", ", snapshot.getChannelNames());
        if (count > 0 && names.length() <= MAX_INLINE_CHANNEL_NAMES_LENGTH) {
            loggerService.debug("Channels: {0} loaded ({1}).", count, names);
        } else {
            loggerService.debug("Channels: {0} loaded.", count);
        }
    }

    /**
     * Report the loaded broadcasts and their combined message count.
     * <p>Suppressed entirely when both global and locale broadcasts are disabled.</p>
     */
    private void reportBroadcasts(StatusSnapshot snapshot) {
        boolean globalEnabled = broadcastService.getConfig().getBoolean(BroadcastConfigKey.GLOBAL_ENABLED);
        boolean localeEnabled = broadcastService.getConfig().getBoolean(BroadcastConfigKey.LOCALE_ENABLED);
        if (!globalEnabled && !localeEnabled) {
            return;
        }
        loggerService.debug("Broadcasts: {0} locale, {1} message(s) in total.",
                snapshot.getBroadcastLocaleCount(), snapshot.getBroadcastMessageTotal());
    }
}
