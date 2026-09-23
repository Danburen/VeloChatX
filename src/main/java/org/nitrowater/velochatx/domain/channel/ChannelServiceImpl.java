package org.nitrowater.velochatx.domain.channel;

import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.waterapi.domain.config.schema.SchemaDefaults;
import org.nitrowater.velochatx.domain.shared.VeloChatXMetaInfoType;
import org.nitrowater.waterapi.domain.config.schema.RegisteredSchema;
import org.nitrowater.waterapi.domain.config.schema.KeyItemSchema;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link ChannelService}.
 * <p>
 * This service manages channel configuration and provides methods for
 * determining server communication capabilities.
 * </p>
 *
 * <p>Configuration loaded from {@code config.yml} and can be reloaded
 * at runtime via {@link KernelConfigUpdateEvent}.</p>
 *
 * <p>Membership is read from either representation:</p>
 * <ul>
 *   <li>legacy: {@code channels.channel-list.<name>.servers} (channel → servers). When any
 *       channel declares a non-empty {@code servers} list, this representation is used and the
 *       new one is ignored;</li>
 *   <li>new: {@code server.list.<id>.channels} (server → channels).</li>
 * </ul>
 * <p>See {@code docs/server-binding-plan.md}.</p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class ChannelServiceImpl implements ChannelService {

    /**
     * config.yml {@code channels.channel-list}. Membership lives on {@code server.list} after
     * migration; {@code servers} is kept optional for legacy configs that have not migrated yet.
     * {@code display} and the {@code welcome} block remain optional.
     */
    private static final RegisteredSchema SCHEMA = new RegisteredSchema(
            ChannelConfigKey.CHANNEL_LIST,
            KeyItemSchema.builder()
                    .optional("servers", List.of())
                    .optional("display", "unknown")
                    .optional("welcome", welcome -> welcome
                            .required("enable", false)
                            .required("message", ""))
                    .build());

    /** Declared defaults of {@link #SCHEMA}, used as the runtime fallback for omitted keys. */
    private final SchemaDefaults schemaDefaults = new SchemaDefaults(SCHEMA.schema().defaults());
    private final ServerService serverService;
    private volatile Config config;
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    /**
     * Create a new ChannelServiceImpl.
     *
     * @param config        the config to read from
     * @param serverService the server service for server lookups
     */
    public ChannelServiceImpl(Config config, ServerService serverService) {
        this.config = config;
        this.serverService = serverService;
        loadChannels();
    }

    @Override
    public List<RegisteredSchema> schemas() {
        return List.of(SCHEMA);
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void onEvent(KernelConfigUpdateEvent event) {
        this.config = event.getConfig();
        loadChannels();
    }

    @Override
    public List<Channel> getAllChannels() {
        return List.copyOf(channels.values());
    }

    @Override
    public Optional<Channel> getChannel(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(channels.get(name));
    }

    @Override
    public Optional<Channel> getChannelForServer(String serverName) {
        for (Channel channel : channels.values()) {
            for (UServer server : channel.getServers()) {
                if (server.getName().equals(serverName)) {
                    return Optional.of(channel);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean canCommunicate(String sourceServerName, String targetServerName) {
        UServer source = serverService.getServer(sourceServerName).orElse(null);
        UServer target = serverService.getServer(targetServerName).orElse(null);
        return canCommunicate(source, target);
    }

    @Override
    public boolean canCommunicate(UServer source, UServer target) {
        // Fail closed: an unresolved server must not silently allow cross-server messages.
        if (source == null || target == null) {
            return false;
        }
        if (getConfig().get(ChannelConfigKey.GLOBAL)) {
            return true;
        }
        Set<String> sourceChannels = new HashSet<>();
        for (Channel channel : channels.values()) {
            if (channel.getServers().contains(source)) {
                sourceChannels.add(channel.getName());
            }
        }
        for (Channel channel : channels.values()) {
            if (channel.getServers().contains(target) && sourceChannels.contains(channel.getName())) {
                return true;
            }
        }
        return false;
    }

    private void loadChannels() {
        channels.clear();
        Map<String, Object> channelMap = getConfig().get(ChannelConfigKey.CHANNEL_LIST);
        if (channelMap == null) {
            return;
        }
        // Legacy channel-centric membership wins when any channel declares a non-empty servers
        // list; otherwise the new server-centric server.list.<id>.channels is used.
        if (legacyMembership(channelMap)) {
            loadLegacyChannels(channelMap);
        } else {
            loadServerCentricChannels(channelMap);
        }
    }

    /**
     * Whether any channel declares a non-empty legacy {@code servers} list, i.e. the legacy
     * membership representation is in use and the new one must be ignored.
     *
     * @param channelMap {@code channels.channel-list} (may be {@code null})
     * @return {@code true} when legacy membership is present
     */
    static boolean legacyMembership(Map<String, Object> channelMap) {
        if (channelMap == null) {
            return false;
        }
        for (Object value : channelMap.values()) {
            if (value instanceof Map<?, ?> map) {
                Object servers = map.get("servers");
                if (servers instanceof List<?> list && !list.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Legacy: channel → servers. */
    private void loadLegacyChannels(Map<String, Object> channelMap) {
        channelMap.forEach((key, value) -> {
            String prefix = ChannelConfigKey.CHANNEL_LIST.getKey() + "." + key;
            List<UServer> servers = getConfig().<String>getCollection(prefix + ".servers",
                            schemaDefaults.<String>list("servers", List.of())).stream()
                    .map(serverService::getServer)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .toList();
            channels.put(key, new Channel(
                    key,
                    getConfig().get(prefix + ".display", schemaDefaults.string("display", "unknown")),
                    getConfig().get(prefix + ".welcome.enable", schemaDefaults.bool("welcome.enable", false)),
                    getConfig().get(prefix + ".welcome.message", schemaDefaults.string("welcome.message", "")),
                    servers
            ));
            servers.forEach(server -> appendServerChannelMeta(server, key));
        });
    }

    /** New: server → channels (channel metadata still comes from channels.channel-list). */
    private void loadServerCentricChannels(Map<String, Object> channelMap) {
        for (String key : channelMap.keySet()) {
            String prefix = ChannelConfigKey.CHANNEL_LIST.getKey() + "." + key;
            channels.put(key, new Channel(
                    key,
                    getConfig().get(prefix + ".display", schemaDefaults.string("display", "unknown")),
                    getConfig().get(prefix + ".welcome.enable", schemaDefaults.bool("welcome.enable", false)),
                    getConfig().get(prefix + ".welcome.message", schemaDefaults.string("welcome.message", "")),
                    List.of()
            ));
        }
        for (UServer server : serverService.getAllServers()) {
            for (String channelName : serverService.getDeclaredChannelNames(server.getName())) {
                Channel channel = channels.get(channelName);
                if (channel != null) {
                    channel.addServer(server);
                    appendServerChannelMeta(server, channelName);
                }
            }
        }
    }

    private void appendServerChannelMeta(UServer server, String channelName) {
        List<String> channelNames = server.<List<String>>getMeta(VeloChatXMetaInfoType.CHANNELS)
                .map(ArrayList::new)
                .orElse(new ArrayList<>());
        if (!channelNames.contains(channelName)) {
            channelNames.add(channelName);
        }
        server.setMeta(VeloChatXMetaInfoType.CHANNELS, channelNames);
    }

    @Override
    public void contributePlaceholders(PlaceholderService service) {
        service.register("channel", ctx -> ctx.getServerName()
                .flatMap(this::getChannelForServer)
                .map(Channel::getDisplayName)
                .orElse(""));
        service.registerDynamic("channel_online", (fullKey, ctx) -> {
            String channelName = fullKey.replace("_channel_online", "");
            return getChannel(channelName)
                    .map(ch -> String.valueOf(ch.getOnlinePlayerCount()))
                    .orElse("0");
        });
    }
}
