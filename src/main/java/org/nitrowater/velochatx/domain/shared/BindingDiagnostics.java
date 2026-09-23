package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.velochatx.domain.channel.Channel;
import org.nitrowater.velochatx.domain.channel.ChannelConfigKey;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.server.ServerConfigKey;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only consistency check between three sources of truth that must agree:
 * <ul>
 *     <li>the backend servers Velocity actually has registered ({@code velocity.toml [servers]}),</li>
 *     <li>the servers declared under {@code server-display.display} / {@code server.list}, and</li>
 *     <li>the servers referenced by the channels.</li>
 * </ul>
 *
 * <p>Any mismatch makes the chat degrade silently: an undeclared backend keeps its raw name and
 * resolves {@code {channel}} to an empty string, while a declared-but-missing backend can never
 * match. This class surfaces those mismatches, graded by consequence so only real problems are
 * warnings, and carries structured arguments so the caller can render them in the source's
 * locale.</p>
 *
 * <p>The analyzer is stateless and never throws; the class is the single source shared by the
 * startup self-check and {@code /velochatx status}.</p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class BindingDiagnostics {

    /**
     * The kind of binding problem, so callers can group, filter or localize issues.
     */
    public enum Kind {
        /** Velocity has the backend registered but no declaration covers it. */
        UNBOUND_SERVER,
        /** A declaration refers to a server that Velocity does not have. */
        PHANTOM_SERVER,
        /** A channel references a server that Velocity does not have. */
        CHANNEL_UNKNOWN_SERVER,
        /** A channel ended up with no valid server after validation. */
        CHANNEL_EMPTY,
        /** A registered backend belongs to no channel while {@code channels.global} is false. */
        SERVER_WITHOUT_CHANNEL
    }

    /** How much the issue matters: {@code WARNING} really breaks cross-server chat. */
    public enum Severity {
        INFO,
        WARNING
    }

    /**
     * A single binding problem, carrying structured arguments for localization.
     *
     * @param kind     the problem category
     * @param severity how much it matters
     * @param args     arguments for the localized template (see {@link #template()})
     */
    public record Issue(Kind kind, Severity severity, Object[] args) {

        /** The i18n template this issue renders through. */
        public VeloChatXSystemMessage template() {
            return switch (kind) {
                case UNBOUND_SERVER -> VeloChatXSystemMessage.BINDING_UNBOUND_SERVER;
                case PHANTOM_SERVER -> VeloChatXSystemMessage.BINDING_PHANTOM_SERVER;
                case CHANNEL_UNKNOWN_SERVER -> VeloChatXSystemMessage.BINDING_CHANNEL_UNKNOWN_SERVER;
                // Empty channels fold their dropped server ids into the second arg so the admin
                // sees the root cause on the same WARNING line instead of a hidden INFO entry.
                case CHANNEL_EMPTY -> hasDroppedServers()
                        ? VeloChatXSystemMessage.BINDING_CHANNEL_EMPTY_DROPPED
                        : VeloChatXSystemMessage.BINDING_CHANNEL_EMPTY;
                case SERVER_WITHOUT_CHANNEL -> VeloChatXSystemMessage.BINDING_SERVER_WITHOUT_CHANNEL;
            };
        }

        /** Whether {@code args[1]} carries a non-blank dropped-server id list. */
        private boolean hasDroppedServers() {
            return args != null && args.length >= 2
                    && args[1] != null && !args[1].toString().isBlank();
        }
    }

    /**
     * Renders a template in some locale. Supplied by the caller so this class stays free of any
     * message-service dependency (and is easy to test).
     */
    @FunctionalInterface
    public interface Renderer {
        String render(VeloChatXSystemMessage template, Object... args);
    }

    private BindingDiagnostics() {
    }

    /**
     * Analyze the current server/channel binding.
     *
     * @param serverService  the server registry
     * @param channelService the channel registry
     * @return an immutable list of issues; empty when everything lines up
     */
    public static List<Issue> analyze(ServerService serverService, ChannelService channelService) {
        List<Issue> issues = new ArrayList<>();
        if (serverService == null || channelService == null) {
            return issues;
        }

        Set<String> registered = new LinkedHashSet<>();
        for (UServer server : serverService.getAllServers()) {
            if (server != null && server.getName() != null) {
                registered.add(server.getName());
            }
        }
        Set<String> bound = new LinkedHashSet<>(serverService.getBoundServerNames());

        Config serverConfig = serverService.getConfig();
        boolean displayEnabled = serverConfig != null
                && ServerConfigKey.isDisplayEnabled(serverConfig);
        Config config = channelService.getConfig();
        boolean global = config == null || Boolean.TRUE.equals(config.getBoolean(ChannelConfigKey.GLOBAL));

        // Registered but not declared. When the display feature is off, the raw-name fallback is
        // the intended behaviour, so this is not reported at all.
        if (displayEnabled) {
            for (String name : registered) {
                if (!bound.contains(name)) {
                    issues.add(new Issue(Kind.UNBOUND_SERVER, Severity.INFO, new Object[]{name}));
                }
            }
        }

        // Declared but Velocity does not have it.
        for (String name : bound) {
            if (!registered.contains(name)) {
                issues.add(new Issue(Kind.PHANTOM_SERVER, Severity.INFO, new Object[]{name}));
            }
        }

        Set<String> serversInChannels = new LinkedHashSet<>();
        for (Channel channel : channelService.getAllChannels()) {
            List<UServer> resolved = channel.getServers();
            for (UServer server : resolved) {
                if (server != null && server.getName() != null) {
                    serversInChannels.add(server.getName());
                }
            }
            List<String> unknownDeclared = new ArrayList<>();
            for (String name : declaredChannelServers(config, channel.getName())) {
                if (!registered.contains(name)) {
                    unknownDeclared.add(name);
                }
            }
            if (resolved.isEmpty()) {
                // Fold dropped ids into the empty-channel warning (one line with root cause)
                // instead of also emitting INFO CHANNEL_UNKNOWN entries the admin never sees.
                Object[] args = unknownDeclared.isEmpty()
                        ? new Object[]{channel.getName()}
                        : new Object[]{channel.getName(), String.join(", ", unknownDeclared)};
                issues.add(new Issue(Kind.CHANNEL_EMPTY, Severity.WARNING, args));
            } else {
                for (String name : unknownDeclared) {
                    issues.add(new Issue(Kind.CHANNEL_UNKNOWN_SERVER,
                            global ? Severity.INFO : Severity.WARNING,
                            new Object[]{channel.getName(), name}));
                }
            }
        }

        // Only a problem when channels are actually isolated; with global=true the backend still chats.
        if (!global) {
            for (String name : registered) {
                if (!serversInChannels.contains(name)) {
                    issues.add(new Issue(Kind.SERVER_WITHOUT_CHANNEL, Severity.WARNING, new Object[]{name}));
                }
            }
        }

        return List.copyOf(issues);
    }

    /**
     * Read the RAW server ids a channel declares (the resolved {@link Channel#getServers()} list
     * has already dropped unknown entries, so it cannot be used to detect them).
     */
    private static List<String> declaredChannelServers(Config config, String channelName) {
        if (config == null || channelName == null) {
            return List.of();
        }
        String path = ChannelConfigKey.CHANNEL_LIST.getKey() + "." + channelName + ".servers";
        List<String> declared = new ArrayList<>(config.getCollection(path, List.<String>of()));
        declared.removeIf(name -> name == null || name.isBlank());
        return declared;
    }

    /**
     * Emit the issues through the plugin logger, graded by severity: real problems go out as
     * WARNING, purely cosmetic ones as debug. A final summary points the admin at
     * {@code /velochatx status} for details.
     *
     * @param logger         the logger to write to (may be {@code null}, in which case this is a no-op)
     * @param issues         the issues produced by {@link #analyze(ServerService, ChannelService)}
     * @param legacyBindings whether legacy binding keys are in use (adds a migration hint)
     * @param renderer       localizes a template + args; may be {@code null} (falls back to the key)
     */
    public static void report(LoggerService logger, List<Issue> issues, boolean legacyBindings,
                              Renderer renderer) {
        if (logger == null) {
            return;
        }
        Renderer r = renderer == null ? (template, args) -> template.getKey() : renderer;
        long warnings = issues == null ? 0 : issues.stream()
                .filter(issue -> issue.severity() == Severity.WARNING)
                .count();
        if (issues == null || issues.isEmpty()) {
            logger.debug(r.render(VeloChatXSystemMessage.BINDING_CHECK_OK));
        } else {
            for (Issue issue : issues) {
                String message = r.render(issue.template(), issue.args());
                if (issue.severity() == Severity.WARNING) {
                    logger.warning(message);
                } else {
                    logger.debug(message);
                }
            }
            long infos = issues.size() - warnings;
            if (warnings > 0) {
                logger.warning(r.render(VeloChatXSystemMessage.BINDING_SUMMARY_WARNING,
                        issues.size(), warnings, infos));
            } else {
                logger.debug(r.render(VeloChatXSystemMessage.BINDING_SUMMARY_INFO, issues.size()));
            }
        }
        // Callers pass legacyBindings=false when a pending config migration already carries
        // the same advice (checkConfigContent prints one combined upgrade note instead).
        if (legacyBindings) {
            logger.warning(r.render(VeloChatXSystemMessage.BINDING_LEGACY_HINT));
        }
    }
}
