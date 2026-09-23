package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.velochatx.domain.broadcast.BroadcastService;
import org.nitrowater.velochatx.domain.channel.Channel;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.waterapi.domain.model.UServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of what the plugin currently has loaded: bound servers (and how many of
 * them are actually online), channels and broadcast messages.
 * <p>
 * This is the single source of truth shared by the {@code /velochatx status} command and the
 * debug-level startup self-check ({@link LoadSummaryReporter}), so both always report the
 * same numbers.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class StatusSnapshot {

    private final List<String> onlineServers;
    private final List<String> offlineServers;
    /** Every backend Velocity has registered (i.e. the real {@code velocity.toml [servers]} ids). */
    private final List<String> registeredServers;
    /** Registered backends that are NOT declared under {@code server-display.display} / {@code server.list}. */
    private final List<String> unboundServers;
    /** Per-server binding state: declared+registered = BOUND, registered-only = UNBOUND, declared-only = PHANTOM. */
    private final Map<String, String> bindingStates;
    private final List<String> channelNames;
    private final int broadcastLocaleCount;
    private final int broadcastGlobalMessages;
    private final int broadcastLocaleMessages;

    private StatusSnapshot(List<String> onlineServers,
                           List<String> offlineServers,
                           List<String> registeredServers,
                           List<String> unboundServers,
                           Map<String, String> bindingStates,
                           List<String> channelNames,
                           int broadcastLocaleCount,
                           int broadcastGlobalMessages,
                           int broadcastLocaleMessages) {
        this.onlineServers = List.copyOf(onlineServers);
        this.offlineServers = List.copyOf(offlineServers);
        this.registeredServers = List.copyOf(registeredServers);
        this.unboundServers = List.copyOf(unboundServers);
        this.bindingStates = new LinkedHashMap<>(bindingStates);
        this.channelNames = List.copyOf(channelNames);
        this.broadcastLocaleCount = broadcastLocaleCount;
        this.broadcastGlobalMessages = broadcastGlobalMessages;
        this.broadcastLocaleMessages = broadcastLocaleMessages;
    }

    /**
     * Collect the current counts from the domain services.
     *
     * @param serverService    server registry
     * @param channelService   channel registry
     * @param broadcastService broadcast registry
     * @return an immutable snapshot; never {@code null}
     */
    public static StatusSnapshot collect(ServerService serverService,
                                         ChannelService channelService,
                                         BroadcastService broadcastService) {
        List<String> online = new ArrayList<>();
        List<String> offline = new ArrayList<>();
        // A declared server is "online" when Velocity actually registered it; otherwise it is a
        // declared-but-not-started (or mistyped) backend.
        for (String name : serverService.getBoundServerNames()) {
            if (serverService.getServer(name).isPresent()) {
                online.add(name);
            } else {
                offline.add(name);
            }
        }

        // Real backends Velocity knows about, and which of them the declaration forgot.
        List<String> registered = serverService.getAllServers().stream()
                .map(UServer::getName)
                .toList();
        Set<String> bound = new HashSet<>(serverService.getBoundServerNames());
        List<String> unbound = registered.stream()
                .filter(name -> !bound.contains(name))
                .toList();

        // Per-server binding state, declared servers first.
        Map<String, String> states = new LinkedHashMap<>();
        for (String name : serverService.getBoundServerNames()) {
            states.put(name, serverService.getBindingState(name)
                    .map(Enum::name).orElse("BOUND"));
        }
        for (String name : registered) {
            states.putIfAbsent(name, serverService.getBindingState(name)
                    .map(Enum::name).orElse("UNBOUND"));
        }

        List<String> channels = channelService.getAllChannels().stream()
                .map(Channel::getName)
                .toList();

        int localeCount = broadcastService.getLocaleBroadcasts().size();
        int globalMessages = broadcastService.getGlobalBroadcast()
                .map(model -> model.getMessages().size())
                .orElse(0);
        int localeMessages = broadcastService.getLocaleBroadcasts().stream()
                .mapToInt(model -> model.getMessages().size())
                .sum();

        return new StatusSnapshot(online, offline, registered, unbound, states,
                channels, localeCount, globalMessages, localeMessages);
    }

    /** Number of servers declared in the config (legacy {@code server-display.display} / {@code server.list}). */
    public int getServerBound() {
        return onlineServers.size() + offlineServers.size();
    }

    /** Number of declared servers that Velocity has registered. */
    public int getServerOnline() {
        return onlineServers.size();
    }

    /** Number of declared servers that are not registered yet. */
    public int getServerOffline() {
        return offlineServers.size();
    }

    public List<String> getOnlineServers() {
        return onlineServers;
    }

    public List<String> getOfflineServers() {
        return offlineServers;
    }

    /** Every backend Velocity has registered (the real {@code velocity.toml [servers]} ids). */
    public List<String> getRegisteredServers() {
        return registeredServers;
    }

    /** Registered backends that are absent from the declaration. */
    public List<String> getUnboundServers() {
        return unboundServers;
    }

    /** Per-server binding state (BOUND / UNBOUND / PHANTOM), in declared-then-registered order. */
    public Map<String, String> getBindingStates() {
        return Map.copyOf(bindingStates);
    }

    /** Number of loaded channels. */
    public int getChannelCount() {
        return channelNames.size();
    }

    public List<String> getChannelNames() {
        return channelNames;
    }

    /** Number of locale (local) broadcast entries. */
    public int getBroadcastLocaleCount() {
        return broadcastLocaleCount;
    }

    public int getBroadcastGlobalMessages() {
        return broadcastGlobalMessages;
    }

    public int getBroadcastLocaleMessages() {
        return broadcastLocaleMessages;
    }

    /** Total broadcast messages across the global and locale broadcasts. */
    public int getBroadcastMessageTotal() {
        return broadcastGlobalMessages + broadcastLocaleMessages;
    }
}
