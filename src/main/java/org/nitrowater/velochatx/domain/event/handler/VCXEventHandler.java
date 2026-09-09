package org.nitrowater.velochatx.domain.event.handler;

import org.nitrowater.velochatx.domain.broadcast.BroadcastConfigKey;
import org.nitrowater.velochatx.domain.broadcast.BroadcastService;
import org.nitrowater.velochatx.domain.channel.Channel;
import org.nitrowater.velochatx.domain.channel.ChannelConfigKey;
import org.nitrowater.velochatx.domain.channel.ChannelService;
import org.nitrowater.velochatx.domain.chat.ChatConfigKey;
import org.nitrowater.velochatx.domain.player.PlayerAttribution;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.velochatx.domain.server.ServerService;
import org.nitrowater.velochatx.domain.shared.VeloChatXMessage;
import org.nitrowater.velochatx.domain.tablist.TabListService;
import org.nitrowater.velochatx.domain.tablist.TabListUpdateTask;
import org.nitrowater.waterapi.domain.event.player.PlayerChatEvent;
import org.nitrowater.velochatx.domain.chat.ChatService;
import org.nitrowater.velochatx.domain.event.VeloChatXGameEventHandler;
import org.nitrowater.velochatx.domain.event.player.PlayerJoinServerEvent;
import org.nitrowater.waterapi.domain.event.player.PlayerDisconnectEvent;
import org.nitrowater.waterapi.domain.event.player.PlayerLoginEvent;
import org.nitrowater.waterapi.domain.logger.MessageLoggerService;
import org.nitrowater.waterapi.domain.message.PlayerMessageService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.shared.common.WColor;
import org.nitrowater.waterapi.domain.spi.PlayerActionService;
import org.nitrowater.waterapi.utils.Colors;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chat event handler implementation.
 * Handles player chat events with ban word filtering and message formatting.
 */
public class VCXEventHandler implements VeloChatXGameEventHandler {

    private final ChatService chatService;
    private final PlayerActionService actionService;
    private final PlayerActionService playerActionService;
    private final PlayerMessageService playerMessageService;
    private final ChannelService channelService;
    private final PlayerService playerService;
    private final MessageLoggerService messageLoggerService;
    private final WProxyServer wProxyServer;
    private final ServerService serverService;
    private final BroadcastService broadcastService;
    private final TabListService tabListService;

    public VCXEventHandler(ChatService chatService, PlayerActionService actionService, PlayerActionService playerActionService, PlayerMessageService playerMessageService, ChannelService channelService, PlayerService playerService, MessageLoggerService messageLoggerService, WProxyServer wProxyServer, ServerService serverService, BroadcastService broadcastService, TabListService tabListService) {
        this.chatService = chatService;
        this.actionService = actionService;
        this.playerActionService = playerActionService;
        this.playerMessageService = playerMessageService;
        this.channelService = channelService;
        this.playerService = playerService;
        this.messageLoggerService = messageLoggerService;
        this.wProxyServer = wProxyServer;
        this.serverService = serverService;
        this.broadcastService = broadcastService;
        this.tabListService = tabListService;
    }

    @Override
    public void onChat(PlayerChatEvent event) {
        UPlayer player = event.getPlayer();
        UUID uuid = player.getUuid();
        String message = event.getMessage();
        PlayerAttribution sourceAttrs = playerService.getOrCreateAttrs(uuid);
        // source player chat offline
        if(sourceAttrs.isChatOffLine()){
            return;
        }
        String finalMessage = chatService.formatMessage(message, player);
        if(chatService.getBooleanConfig(ChatConfigKey.BAN_WORDS_ENABLED)) {
            if (chatService.hasBannedWords(message)) {
                playerActionService.sendMessage(
                        player,
                        playerMessageService.getMessage("ban-words-message"),
                        WColor.RED
                );
                if (chatService.getBooleanConfig(ChatConfigKey.BAN_WORDS_LOG)) {
                    PlaceholderContext ctx = PlaceholderContext.builder()
                            .dynamicParams(Map.of("message", finalMessage)).build();
                    messageLoggerService.auto(VeloChatXMessage.BAN_WORDS_LOG, ctx);
                }
                return;
            }
        }
        if(chatService.getBooleanConfig(ChatConfigKey.LOG_CHAT_ENABLED)) {
            // convert color to console
            if(chatService.getBooleanConfig(ChatConfigKey.LOG_CHAT_CONVERT)){
                messageLoggerService.getLoggerService().info(
                        Colors.parseColorToAnsi(finalMessage)
                );
            } else {
                messageLoggerService.getLoggerService().info(
                        Colors.stripAllColors(finalMessage)
                );
            }
        }
        if(chatService.getBooleanConfig(ChatConfigKey.CROSSING_CHAT_ENABLED)) {
            String sourceServerName = player.getCurrentServer()
                    .map(UServer::getName)
                    .orElse("unknown");

            wProxyServer.getAllOnlinePlayers().forEach(innerPlayer -> {
                UUID innerUuid = innerPlayer.getUuid();
                PlayerAttribution attrs = playerService.getOrCreateAttrs(innerUuid);
                String playerServerName = innerPlayer.getCurrentServer()
                        .map(UServer::getName)
                        .orElse("unknown");
                if(attrs.isChatOffLine()) return;
                // black list
                if(attrs.getIgnorePlayers().contains(uuid)) return;
                // filter same server
                if(playerServerName.equals(sourceServerName)) return;
                // same channel communicate
                if(channelService.canCommunicate(playerServerName,sourceServerName)) {
                    playerActionService.sendMessage(
                            innerPlayer,
                            finalMessage
                    );
                };
            });
        }
    }

    @Override
    public void onJoinServer(PlayerJoinServerEvent event) {
        UPlayer player = event.getPlayer();
        // Load locale
        player.getEffectiveLocale().ifPresent(lang -> {
            try {
                messageLoggerService.getPluginMessageService().loadLocale(lang.getLanguage());
            } catch (IOException e) {
                messageLoggerService.warn(VeloChatXMessage.MESSAGE_LOCALE_CANT_LOAD, player.getLoginName(), lang);
            }
        });
        // Broadcast join/leave messages
        UServer currServer = serverService.getOrCreate(event.getServerName());
        UServer prevServer = Optional.ofNullable(event.getPreviousServerName())
                .map(serverService::getOrCreate)
                .orElse(null);
        broadcastService.broadcastPlayerJoin(player, currServer, prevServer);
        // Send channel welcome message
        Channel channel = channelService.getChannelForServer(currServer.getName());
        if (channelService.getConfig().get(
                ChannelConfigKey.CHANNEL_LIST.getKey() + "." + channel.getName() + ".welcome.enable",
                true)
        ) {
            String welcomeMessage = messageLoggerService
                    .getPlaceholderService()
                    .resolve(
                            channelService.getConfig().get(
                                    ChannelConfigKey.CHANNEL_LIST.getKey() + "." + channel.getName() + ".welcome.message",
                                    "Welcome to the channel!"
                            ),
                            PlaceholderContext.builder()
                                    .player(player)
                                    .serverName(currServer.getName())
                                    .build()
                    );
            playerActionService.sendMessage(player, welcomeMessage);
        }
        // Update tab-list
        tabListService.queueUpdate(new TabListUpdateTask(
                player.getUuid(),
                TabListUpdateTask.UpdateType.HEADER_FOOTER,
                System.currentTimeMillis()
        ));
        tabListService.queueUpdate(new TabListUpdateTask(
                player.getUuid(),
                TabListUpdateTask.UpdateType.ENTRY,
                System.currentTimeMillis()
        ));
        wProxyServer.getAllOnlinePlayers().forEach(p -> {
            if (!p.getUuid().equals(player.getUuid())) {
                tabListService.queueUpdate(new TabListUpdateTask(
                        p.getUuid(),
                        TabListUpdateTask.UpdateType.HEADER_FOOTER,
                        System.currentTimeMillis()
                ));
            }
        });
    }

    @Override
    public void onLogin(PlayerLoginEvent event) {
        UPlayer player = event.getPlayer();
        boolean isFirstJoin = playerService.storeOrUpdatePlayerBaseInfo(player);
        // Broadcast proxy join (broadcast to all)
        broadcastService.broadcastProxyJoin(player);
        // Send welcome message
        if (broadcastService.getBooleanConfig(BroadcastConfigKey.WELCOME_ENABLED)) {
            if (isFirstJoin || ! broadcastService.getBooleanConfig(BroadcastConfigKey.WELCOME_ONLY_FIRST_JOIN)) {
                String welcomeMessage = messageLoggerService
                        .getPlaceholderService()
                        .resolve(
                                broadcastService.getConfig(BroadcastConfigKey.WELCOME_MESSAGE),
                                PlaceholderContext.builder()
                                        .player(player)
                                        .build()
                        );
                playerActionService.sendMessage(player, welcomeMessage);
            }
        }
    }

    @Override
    public void onDisconnect(PlayerDisconnectEvent event) {
        UPlayer player = event.getPlayer();
        // Broadcast proxy leave (broadcast to all)
        broadcastService.broadcastProxyLeave(player);
        playerService.updateLeaveTime(player.getUuid(), System.currentTimeMillis());
        // Update tablist - remove player from all online players' tablist
        UUID uuid = player.getUuid();
        wProxyServer.getAllOnlinePlayers().forEach(onlinePlayer -> {
            tabListService.queueUpdate(new TabListUpdateTask(
                    uuid,
                    onlinePlayer.getUuid(),
                    TabListUpdateTask.UpdateType.REMOVE,
                    System.currentTimeMillis()
            ));
        });
    }
}
