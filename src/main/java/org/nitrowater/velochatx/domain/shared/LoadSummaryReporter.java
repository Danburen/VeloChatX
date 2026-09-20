package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.velochatx.domain.broadcast.BroadcastConfigKey;
import org.nitrowater.velochatx.domain.broadcast.BroadcastService;
import org.nitrowater.velochatx.domain.channel.Channel;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.message.MessageService;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Logs a one-shot self-check summary while the plugin is loading its servers,
 * channels and broadcasts.
 * <p>
 * Every line is sourced from {@code system-message/*.yml} (see
 * {@link VeloChatXSystemMessage}) so it follows the plugin locale and can carry
 * console color codes. Call {@link #report()} once, right after the services have
 * been wired on first load.
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
    private final MessageService messageService;
    private final LoggerService loggerService;

    public LoadSummaryReporter(ServerService serverService,
                               ChannelService channelService,
                               BroadcastService broadcastService,
                               MessageService messageService,
                               LoggerService loggerService) {
        this.serverService = serverService;
        this.channelService = channelService;
        this.broadcastService = broadcastService;
        this.messageService = messageService;
        this.loggerService = loggerService;
    }

    /**
     * Report the loaded servers, channels and broadcasts to the console.
     * <p>No-op when the kernel has no message/logger service (degraded startup).</p>
     */
    public void report() {
        if (messageService == null || loggerService == null) {
            return;
        }
        reportServers();
        reportChannels();
        reportBroadcasts();
    }

    /**
     * Report the servers bound in {@code server-display.display}.
     * <p>
     * A bound server is "online" when it is actually registered in Velocity;
     * otherwise it is reported as not online yet (typically a typo or a backend
     * that has not started). Nothing is logged when no server is bound.
     * </p>
     */
    private void reportServers() {
        List<String> bound = serverService.getBoundServerNames();
        if (bound.isEmpty()) {
            return;
        }
        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        for (String name : bound) {
            if (serverService.getServer(name).isPresent()) {
                online.add(name);
            } else {
                offline.add(name);
            }
        }
        int total = bound.size();
        if (offline.isEmpty()) {
            log(VeloChatXSystemMessage.SERVER_LOAD_ALL_ONLINE, total);
        } else if (online.isEmpty()) {
            log(VeloChatXSystemMessage.SERVER_LOAD_NONE_ONLINE, total, String.join(", ", offline));
        } else {
            log(VeloChatXSystemMessage.SERVER_LOAD_PARTIAL, total,
                    String.join(", ", online), String.join(", ", offline));
        }
    }

    private void reportChannels() {
        List<Channel> channels = channelService.getAllChannels();
        int count = channels.size();
        String names = channels.stream().map(Channel::getName).collect(Collectors.joining(", "));
        if (count > 0 && names.length() <= MAX_INLINE_CHANNEL_NAMES_LENGTH) {
            log(VeloChatXSystemMessage.CHANNEL_LOAD, count, names);
        } else {
            log(VeloChatXSystemMessage.CHANNEL_LOAD_COUNT, count);
        }
    }

    /**
     * Report the loaded broadcasts and their combined message count.
     * <p>
     * Suppressed entirely when both global and locale broadcasts are disabled.
     * The sub-broadcast count is the number of locale entries (only loaded when
     * {@code locale.enable} is on); the message total adds the global list on top.
     * </p>
     */
    private void reportBroadcasts() {
        boolean globalEnabled = broadcastService.getConfig().getBoolean(BroadcastConfigKey.GLOBAL_ENABLED);
        boolean localeEnabled = broadcastService.getConfig().getBoolean(BroadcastConfigKey.LOCALE_ENABLED);
        if (!globalEnabled && !localeEnabled) {
            return;
        }
        int localeCount = broadcastService.getLocaleBroadcasts().size();
        int globalMessages = broadcastService.getGlobalBroadcast()
                .map(bct -> bct.getMessages().size())
                .orElse(0);
        int localeMessages = broadcastService.getLocaleBroadcasts().stream()
                .mapToInt(bct -> bct.getMessages().size())
                .sum();
        log(VeloChatXSystemMessage.BROADCAST_LOAD, localeCount, globalMessages + localeMessages);
    }

    private void log(VeloChatXSystemMessage template, Object... args) {
        String message = messageService.getSystemMessage(template.getKey());
        if (args != null && args.length > 0) {
            try {
                message = String.format(message, args);
            } catch (IllegalFormatException ignored) {
                // Keep the raw template rather than dropping the line entirely.
            }
        }
        loggerService.logMessage(message);
    }
}
