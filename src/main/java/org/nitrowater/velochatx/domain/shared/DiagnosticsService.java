package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.velochatx.domain.broadcast.BroadcastConfigKey;
import org.nitrowater.velochatx.domain.broadcast.BroadcastService;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.waterapi.domain.config.application.ConfigManager;
import org.nitrowater.waterapi.domain.kernel.Kernel;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.message.MessageService;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.List;

/**
 * Plugin-side diagnostics facade: startup load summary, binding analysis, and the
 * binding-problem log report — one entry point for {@code ServiceFactory} and commands.
 * <p>
 * Config-file differences (missing keys / version lag / pending migrations / schema
 * violations) are intentionally <b>not</b> handled here: WaterAPI already owns that via
 * {@link Kernel#checkConfigContent()} and {@link org.nitrowater.waterapi.domain.config.audit.ConfigNote}.
 * Plugins contribute with {@link Kernel#registerConfigCheck} instead of re-implementing.
 * </p>
 * <p>
 * Pure analysis stays in {@link BindingDiagnostics} (static, unit-testable); the immutable
 * numbers stay in {@link StatusSnapshot}. This class only orchestrates logging and localization.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class DiagnosticsService {

    /** Inline the channel name list only while it still fits comfortably on one line. */
    private static final int MAX_INLINE_CHANNEL_NAMES_LENGTH = 48;

    private final ServerService serverService;
    private final ChannelService channelService;
    private final BroadcastService broadcastService;
    private final Kernel kernel;

    public DiagnosticsService(ServerService serverService,
                              ChannelService channelService,
                              BroadcastService broadcastService,
                              Kernel kernel) {
        this.serverService = serverService;
        this.channelService = channelService;
        this.broadcastService = broadcastService;
        this.kernel = kernel;
    }

    /**
     * Collect the shared status snapshot used by the startup summary and {@code /velochatx status}.
     *
     * @return an immutable snapshot; never {@code null}
     */
    public StatusSnapshot snapshot() {
        return StatusSnapshot.collect(serverService, channelService, broadcastService);
    }

    /**
     * Analyze the mismatch between Velocity's registered backends, declarations and channels.
     * Shared by the startup self-check and {@code /velochatx status}.
     *
     * @return the current binding issues; empty when everything lines up
     */
    public List<BindingDiagnostics.Issue> analyzeBindings() {
        return BindingDiagnostics.analyze(serverService, channelService);
    }

    /**
     * Log the one-shot startup self-check summary for the loaded servers, channels and
     * broadcasts at debug level. Intended to run once on first load.
     * <p>No-op when the kernel has no logger service (degraded startup).</p>
     * <p>Numbers come from {@link StatusSnapshot} so they never drift from {@code /velochatx status}.
     * "online/offline" = declared ∩ Velocity-registered, not process liveness.</p>
     */
    public void reportLoadSummary() {
        LoggerService logger = kernel.getLoggerService();
        if (logger == null) {
            return;
        }
        StatusSnapshot snapshot = snapshot();
        int declared = snapshot.getServerBound();
        if (declared > 0) {
            if (snapshot.getServerOffline() == 0) {
                logger.debug("Servers: {0} declared, all registered by Velocity.", declared);
            } else if (snapshot.getServerOnline() == 0) {
                logger.debug("Servers: {0} declared, none registered yet: {1}.",
                        declared, String.join(", ", snapshot.getOfflineServers()));
            } else {
                logger.debug("Servers: {0} declared, registered: {1}; missing: {2}.",
                        declared,
                        String.join(", ", snapshot.getOnlineServers()),
                        String.join(", ", snapshot.getOfflineServers()));
            }
        }

        int channelCount = snapshot.getChannelCount();
        String channelNames = String.join(", ", snapshot.getChannelNames());
        if (channelCount > 0 && channelNames.length() <= MAX_INLINE_CHANNEL_NAMES_LENGTH) {
            logger.debug("Channels: {0} loaded ({1}).", channelCount, channelNames);
        } else {
            logger.debug("Channels: {0} loaded.", channelCount);
        }

        boolean globalEnabled = broadcastService.getConfig().getBoolean(BroadcastConfigKey.GLOBAL_ENABLED);
        boolean localeEnabled = broadcastService.getConfig().getBoolean(BroadcastConfigKey.LOCALE_ENABLED);
        if (globalEnabled || localeEnabled) {
            logger.debug("Broadcasts: {0} locale, {1} message(s) in total.",
                    snapshot.getBroadcastLocaleCount(), snapshot.getBroadcastMessageTotal());
        }
    }

    /**
     * Log every server/channel binding problem at WARNING level once during startup, so admins
     * see why a backend may be printed with its raw name or an empty {@code {channel}}.
     * <p>The legacy-binding migration hint is suppressed when {@code config} already has a
     * pending migration: {@code checkConfigContent} prints one combined upgrade note instead.</p>
     */
    public void reportBindingProblems() {
        boolean legacy = serverService.hasLegacyBindings();
        boolean migrationCarriesHint = false;
        ConfigManager manager = kernel.getConfigManager();
        if (manager != null) {
            migrationCarriesHint = manager.hasPendingMigration("config");
        }
        BindingDiagnostics.report(
                kernel.getLoggerService(),
                analyzeBindings(),
                legacy && !migrationCarriesHint,
                messageRenderer()
        );
    }

    /**
     * Build a localizer for binding diagnostics: resolves the template through the kernel's
     * message service in the server locale, then applies {@link String#format}.
     */
    private BindingDiagnostics.Renderer messageRenderer() {
        MessageService messageService = kernel.getMessageService();
        String lang = kernel.getLocale().getLanguage();
        return (template, args) -> {
            String message = messageService.getMessage(template, lang);
            if (args == null || args.length == 0) {
                return message;
            }
            try {
                return String.format(message, args);
            } catch (java.util.IllegalFormatException e) {
                return message;
            }
        };
    }
}
