package org.nitrowater.velochatx.domain.broadcast;

import org.nitrowater.velochatx.domain.channel.Channel;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.waterapi.domain.kernel.ConfigLoader;
import org.nitrowater.waterapi.domain.kernel.LocaleConfig;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.config.ConfigData;
import org.nitrowater.waterapi.domain.shared.config.FileConfigData;
import org.nitrowater.waterapi.domain.shared.service.SchedulerService;
import org.nitrowater.waterapi.domain.spi.PlayerActionService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation of {@link BroadcastService}.
 * <p>
 * This service manages broadcast messages using {@link BroadcastModel}.
 * It reads configuration from {@code broadcast.yml}.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class BroadcastServiceImpl implements BroadcastService {

    private volatile Config broadcastConfig;
    private final ServerService serverService;
    private final ChannelService channelService;
    private final WProxyServer wProxyServer;
    private final PlaceholderService placeholderService;
    private final PlayerActionService actionService;
    private final SchedulerService scheduler;
    private final LoggerService loggerService;
    /** Loads/reloads {@code broadcast.yml} (extract-on-first-run + read). */
    private final ConfigLoader configLoader;
    /** Language chain used to locate the shipped default broadcast.yml. */
    private final LocaleConfig localeConfig;

    private volatile BroadcastModel globalBroadcast;
    private final List<BroadcastModel> localeBroadcasts = new ArrayList<>();
    private final Map<String, List<BroadcastModel>> serverBroadcasts = new ConcurrentHashMap<>();
    private volatile SchedulerService.TaskHandle broadcastTaskHandle;

    public BroadcastServiceImpl(ConfigLoader configLoader, LocaleConfig localeConfig, ServerService serverService,
                                ChannelService channelService, WProxyServer wProxyServer,
                                PlaceholderService placeholderService, PlayerActionService actionService,
                                SchedulerService scheduler, LoggerService loggerService) {
        this.configLoader = configLoader;
        this.localeConfig = localeConfig;
        this.serverService = serverService;
        this.channelService = channelService;
        this.wProxyServer = wProxyServer;
        this.placeholderService = placeholderService;
        this.actionService = actionService;
        this.scheduler = scheduler;
        this.loggerService = loggerService;
        this.broadcastConfig = loadBroadcastConfig();
        loadBroadcasts();
    }

    @Override
    public Config getConfig() {
        return broadcastConfig;
    }

    @Override
    public ConfigData getConfigData() {
        return broadcastConfig.getConfigData();
    }

    @Override
    public String getConfigBaseName() {
        return "broadcast";
    }

    /**
     * Re-read {@code broadcast.yml} from disk and refresh all broadcast models.
     */
    @Override
    public void reload() {
        updateConfig(loadBroadcastConfig());
    }

    /**
     * Read {@code broadcast.yml}, creating it from the shipped default on first run.
     * <p>Falls back to an empty config so a missing/broken file degrades to defaults
     * instead of throwing.</p>
     */
    private Config loadBroadcastConfig() {
        if (configLoader == null || localeConfig == null) {
            loggerService.warning("Broadcast config loader unavailable, using defaults.");
            return new Config(new FileConfigData(new HashMap<>()));
        }
        try {
            return new Config(configLoader.loadOrCreate("broadcast", localeConfig.getLanguageChain()));
        } catch (IOException e) {
            loggerService.warning("Broadcast config is unavailable, using defaults: " + e.getMessage());
            return new Config(new FileConfigData(new HashMap<>()));
        }
    }

    @Override
    public void onEvent(KernelConfigUpdateEvent event) {
        loadBroadcasts();
    }

    @Override
    public void updateConfig(Config config) {
        if (config == null) {
            return;
        }
        this.broadcastConfig = config;
        loadBroadcasts();
    }

    @Override
    public Optional<BroadcastModel> getGlobalBroadcast() {
        return Optional.ofNullable(globalBroadcast);
    }

    @Override
    public List<BroadcastModel> getLocaleBroadcasts() {
        return List.copyOf(localeBroadcasts);
    }

    @Override
    public Optional<BroadcastModel> getLocaleBroadcast(String name) {
        return localeBroadcasts.stream()
                .filter(bct -> bct.getName().equals(name))
                .findFirst();
    }

    @Override
    public List<BroadcastModel> getBroadcastsForServer(String serverName) {
        return serverBroadcasts.getOrDefault(serverName, List.of());
    }

    @Override
    public List<String> getMessages(String serverName) {
        return serverBroadcasts.getOrDefault(serverName, List.of()).stream()
                .flatMap(bct -> bct.getMessages().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public String getPrefix(String serverName) {
        List<BroadcastModel> broadcasts = serverBroadcasts.getOrDefault(serverName, List.of());
        // Locale broadcasts are appended after the global one, so the last non-empty
        // prefix wins (documented behaviour: locale prefix overrides global).
        for (int i = broadcasts.size() - 1; i >= 0; i--) {
            String prefix = broadcasts.get(i).getPrefix();
            if (prefix != null && !prefix.isEmpty()) {
                return prefix;
            }
        }
        return "";
    }

    @Override
    public int getMessageCount(String serverName) {
        return getMessages(serverName).size();
    }

    @Override
    public void broadcastPlayerJoin(UPlayer player, UServer currentServer, UServer previousServer) {
        String currentChannelName = channelService.getChannelForServer(currentServer.getName())
                .map(Channel::getName).orElse(null);
        String previousChannelName = previousServer != null
                ? channelService.getChannelForServer(previousServer.getName()).map(Channel::getName).orElse(null)
                : null;
        boolean isChannelSwitch = previousServer != null
                && !java.util.Objects.equals(currentChannelName, previousChannelName);

        boolean overlay = getConfig().getBoolean(BroadcastConfigKey.ENTRY_HIERARCHICAL_OVERLAY);
        // Level 1 (highest): Player switches channel
        if (isChannelSwitch) {
            if (!getConfig().getBoolean(BroadcastConfigKey.CHANNEL_ENABLED)) return;
            String message = getConfig().get(BroadcastConfigKey.CHANNEL_JOIN_MESSAGE);
            String resolved = resolveMessage(message, player, currentServer.getName());
            logToConsole(resolved);
            // A JOIN goes to the channel being entered (the current one).
            broadcastByScope(resolved, getConfig().get(BroadcastConfigKey.CHANNEL_SCOPE), player, currentChannelName);
            if (overlay) return;
        }
        // Level 2 (lowest): Player joins backend
        if (!getConfig().getBoolean(BroadcastConfigKey.BACKEND_ENABLED)) return;
        String message = getConfig().get(BroadcastConfigKey.BACKEND_JOIN_MESSAGE);
        String resolved = resolveMessage(message, player, currentServer.getName());
        logToConsole(resolved);
        broadcastByScope(resolved, getConfig().get(BroadcastConfigKey.BACKEND_SCOPE), player, currentChannelName);
    }

    @Override
    public void broadcastProxyJoin(UPlayer player) {
        if (!getConfig().getBoolean(BroadcastConfigKey.PROXY_ENABLED)) return;
        String message = getConfig().get(BroadcastConfigKey.PROXY_JOIN_MESSAGE);
        String resolved = resolveMessage(message, player,
                player.getCurrentServer().map(UServer::getName).orElse(null));
        logToConsole(resolved);
        actionService.broadcastToAll(resolved);
    }

    @Override
    public void broadcastPlayerLeave(UPlayer player, UServer previousServer) {
        String currentServerName = player.getCurrentServer().map(UServer::getName).orElse(null);
        String previousChannelName = channelService.getChannelForServer(previousServer.getName())
                .map(Channel::getName).orElse(null);
        String currentChannelName = currentServerName != null
                ? channelService.getChannelForServer(currentServerName).map(Channel::getName).orElse(null)
                : null;
        boolean isChannelSwitch = currentServerName != null
                && !java.util.Objects.equals(previousChannelName, currentChannelName);

        boolean overlay = getConfig().getBoolean(BroadcastConfigKey.ENTRY_HIERARCHICAL_OVERLAY);
        // Level 1 (highest): Player switches channel
        if (isChannelSwitch) {
            if (!getConfig().getBoolean(BroadcastConfigKey.CHANNEL_ENABLED)) return;
            String message = getConfig().get(BroadcastConfigKey.CHANNEL_LEAVE_MESSAGE);
            String resolved = resolveMessage(message, player, previousServer.getName());
            logToConsole(resolved);
            // A LEAVE goes to the channel being left (the previous one).
            broadcastByScope(resolved, getConfig().get(BroadcastConfigKey.CHANNEL_SCOPE), player, previousChannelName);
            if (overlay) return;
        }
        // Level 2 (lowest): Player leaves backend
        if (!getConfig().getBoolean(BroadcastConfigKey.BACKEND_ENABLED)) return;
        String message = getConfig().get(BroadcastConfigKey.BACKEND_LEAVE_MESSAGE);
        String resolved = resolveMessage(message, player, previousServer.getName());
        logToConsole(resolved);
        broadcastByScope(resolved, getConfig().get(BroadcastConfigKey.BACKEND_SCOPE), player, previousChannelName);
    }

    @Override
    public void broadcastProxyLeave(UPlayer player) {
        if (!getConfig().getBoolean(BroadcastConfigKey.PROXY_ENABLED)) return;
        String message = getConfig().get(BroadcastConfigKey.PROXY_LEAVE_MESSAGE);
        String resolved = resolveMessage(message, player,
                player.getCurrentServer().map(UServer::getName).orElse(null));
        logToConsole(resolved);
        actionService.broadcastToAll(resolved);
    }

    private void broadcastByScope(String message, String scope, UPlayer player, String channelName) {
        switch (scope) {
            case "proxy":
                actionService.broadcastToAll(message);
                break;
            case "channel":
                if (channelName != null) {
                    broadcastToChannel(channelName, message);
                } else {
                    // Fallback: broadcast to current server's channel
                    player.getCurrentServer().ifPresent(server ->
                            channelService.getChannelForServer(server.getName())
                                    .map(Channel::getName)
                                    .ifPresent(channel -> broadcastToChannel(channel, message))
                    );
                }
                break;
            case "server":
            default:
                // Broadcast to current server only
                player.getCurrentServer().ifPresent(server -> {
                    actionService.broadcastToServer(server, message);
                });
                break;
        }
    }

    private void broadcastToChannel(String channelName, String message) {
        channelService.getChannel(channelName).ifPresent(channel -> {
            channel.getServers().forEach(server -> {
                actionService.broadcastToServer(server, message);
            });
        });
    }

    private String resolveMessage(String template, UPlayer player, String serverName) {
        PlaceholderContext context = PlaceholderContext.builder()
                .player(player)
                .serverName(serverName)
                .build();
        return placeholderService.resolve(template, context);
    }

    private void logToConsole(String message) {
        if (getConfig().get(BroadcastConfigKey.ENTRY_LOG_TO_CONSOLE)) {
            loggerService.info("[Broadcast] " + message);
        }
    }

    private void loadBroadcasts() {
        globalBroadcast = null;
        localeBroadcasts.clear();
        serverBroadcasts.clear();

        boolean globalEnabled = broadcastConfig.get(BroadcastConfigKey.GLOBAL_ENABLED);
        boolean localeEnabled = broadcastConfig.get(BroadcastConfigKey.LOCALE_ENABLED);

        if (globalEnabled) {
            String globalPrefix = broadcastConfig.get(BroadcastConfigKey.GLOBAL_PREFIX);
            List<String> globalMessages = broadcastConfig.get("global.message-list", List.of());

            globalBroadcast = new BroadcastModel(
                    "global",
                    globalPrefix,
                    globalMessages,
                    channelService.getAllChannels(),
                    serverService.getAllServers()
            );
        }

        if (localeEnabled) {
            Map<String, Object> broadcastMap = broadcastConfig.get("locale.broadcast-list", Map.of());
            broadcastMap.forEach((key, value) -> {
                String pathPrefix = "locale.broadcast-list." + key;
                boolean enabled = broadcastConfig.get(pathPrefix + ".enable", false);
                if (enabled) {
                    String prefix = broadcastConfig.get(pathPrefix + ".prefix", "");
                    List<String> messageList = broadcastConfig.get(pathPrefix + ".message-list", List.of());
                    List<String> serverNames = broadcastConfig.get(pathPrefix + ".server-list", List.of());
                    List<String> channelNames = broadcastConfig.get(pathPrefix + ".channel-list", List.of());

                    List<UServer> servers = serverNames.stream()
                            .filter(name -> name != null && !name.isBlank())
                            .map(serverService::getServer)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList());

                    List<Channel> channels = channelNames.stream()
                            .filter(name -> name != null && !name.isBlank())
                            .map(channelService::getChannel)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(Collectors.toList());

                    BroadcastModel localeBct = new BroadcastModel(key, prefix, messageList, channels, servers);
                    localeBroadcasts.add(localeBct);
                }
            });
        }
        buildServerBroadcastsMap();
    }

    private void buildServerBroadcastsMap() {
        serverService.getAllServers().forEach(server ->
                serverBroadcasts.put(server.getName(), new ArrayList<>())
        );
        if (globalBroadcast != null) {
            serverService.getAllServers().forEach(server ->
                    serverBroadcasts.get(server.getName()).add(globalBroadcast)
            );
        }
        localeBroadcasts.forEach(bct ->
                bct.getServers().forEach(server ->
                        serverBroadcasts.computeIfAbsent(server.getName(), k -> new ArrayList<>())
                                .add(bct)
                )
        );
    }

    @Override
    public void startScheduler() {
        if (broadcastTaskHandle != null) return;

        long interval = getConfig().get(BroadcastConfigKey.INTERVAL);
        boolean globalEnabled = getConfig().getBoolean(BroadcastConfigKey.GLOBAL_ENABLED);
        boolean localeEnabled = getConfig().getBoolean(BroadcastConfigKey.LOCALE_ENABLED);

        if (!globalEnabled && !localeEnabled) return;

        broadcastTaskHandle = scheduler.scheduleAtFixedRate(
                this::executeTimedBroadcast,
                interval,
                interval,
                TimeUnit.SECONDS
        );
    }

    @Override
    public void stopScheduler() {
        if (broadcastTaskHandle != null) {
            broadcastTaskHandle.cancel();
            broadcastTaskHandle = null;
        }
    }

    private void executeTimedBroadcast() {
        // Broadcast once per server (not per online player) to avoid duplicate messages.
        serverService.getAllServers().forEach(server -> {
            String serverName = server.getName();
            List<String> messages = getMessages(serverName);
            if (messages.isEmpty()) return;

            String prefix = getPrefix(serverName);
            String message = messages.get((int) (Math.random() * messages.size()));
            PlaceholderContext context = PlaceholderContext.builder().serverName(serverName).build();
            String resolved = prefix + placeholderService.resolve(message, context);
            actionService.broadcastToServer(server, resolved);
        });
    }
}
