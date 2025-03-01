package me.waterwood.velochatx.events;


import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.velochatx.VeloChatX;
import me.waterwood.velochatx.manager.*;
import org.waterwood.plugin.velocity.util.MethodBase;
import me.waterwood.velochatx.utils.Channel;
import me.waterwood.velochatx.utils.SubServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.waterwood.velochatx.utils.PlayerAttribution;
import me.waterwood.velochatx.manager.BasicMethods;
import org.waterwood.plugin.velocity.VelocityPlugin;
import org.waterwood.utils.Colors;

import java.util.*;

public class PlayerEvents extends MethodBase {
    private final ProxyServer proxyServer = VelocityPlugin.getProxyServer();
    private static final Map<UUID,PlayerAttribution> playerAttrs;

    static{
        playerAttrs = new HashMap<>();
    }

    public static Map<UUID, PlayerAttribution> getPlayerAttrs() {
        return playerAttrs;
    }

    @Subscribe(priority = 0)
    public void onPlayChat(PlayerChatEvent evt){
        String message = evt.getMessage();
        Player source = evt.getPlayer();
        UUID sourceUuid = source.getUniqueId();
        // source player chat offline
        if(playerAttrs.get(sourceUuid).isChatOffLine()){
            return;
        }
        if(ChatManager.isBanWorldEnable()) {
            if (ChatManager.hasBanWords(message)) {
                source.sendMessage(Component.text(MessageManager.getMessage("ban-words-message"), NamedTextColor.RED));
                if (ChatManager.isBanWordLog()) {
                    getLogger().info(Colors.parseColor(ChatManager.placeChatValue(MessageManager.getPluginMessage("ban-words-log-message"), message, source)));
                }
                return;
            }
        }
        String finalMessage = ChatManager.placeChatValue(message,source);
        if(LogManager.isLogToConsole()) getLogger().info(Colors.parseColor(finalMessage,! LogManager.isLogColorConvert()));
        String sourceServerName = source.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse("unknown");
        proxyServer.getAllPlayers().forEach(player -> {
            UUID uuid = player.getUniqueId();
            String playerServerName = player.getCurrentServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse("unknown");
            //chat offline
            if(playerAttrs.get(uuid).isChatOffLine()) return;
            // same server
            if(playerServerName.equals(sourceServerName)) return;
            // black list
            if(playerAttrs.get(uuid).getIgnorePlayers().contains(sourceUuid)) return;
            // same channel communicate
            if(!ChatManager.canCommunicate(playerServerName,sourceServerName) && ! BroadCastManager.isChannelGlobal()) return;
            player.sendMessage(Component.text(finalMessage));
        });
    }

    @Subscribe(priority = 0)
    public void onConnectServer(ServerConnectedEvent evt){
        Player player = evt.getPlayer();
        RegisteredServer server = evt.getServer();

        try {
            VeloChatX.getInstance().loadLocale(PlayerManager.getPlayerLangCode(player));
        }catch(NullPointerException e){
            getLogger().info(getPluginMessage("cant-load-message"));
        }finally {
            // player join backhand server broadcast
            evt.getPreviousServer().ifPresent(preServer -> {
                        MessageManager.broadcastServerMessage(player,preServer,false);
                        BasicMethods.getSubServer(preServer.getServerInfo().getName()).removePlayer(player);
                    });
            MessageManager.broadcastServerMessage(player,server,true);

            // welcome message broadcast
            SubServer subServer = BasicMethods.getSubServer(server.getServerInfo().getName());
            subServer.addPlayer(player);
            Channel channel = subServer.getSourceChannel();
            if (channel != null && channel.isWelcomeEnable()) {
                MessageManager.broadcastMessage(player,channel.getWelcomeMessage());
            }

            // tab list update
            if(TabListManager.isTabListEnable()) {
                proxyServer.getAllPlayers().forEach(p ->{
                    TabListManager.tabListAddPlayer(player,p);
                    TabListManager.tabListAddPlayer(p,player);
                });
            }

        }
    }
    @Subscribe(priority = 0)
    public void onProxyConnect(LoginEvent evt){
        Player player = evt.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean isFirstJoin = ! PlayerManager.storeOrUpdatePlayer(player);
        // add player data to memory
        if(isFirstJoin){
            playerAttrs.put(uuid, new PlayerAttribution(new HashSet<>(), new HashSet<>()));
        }else{
            playerAttrs.put(uuid,PlayerManager.getPlayerAttribution(uuid));
        }

        // broadcast proxy join message(multiple player)
        MessageManager.broadcastProxyMessage(player,true);
        //welcome message broadcast(single player)
        if(BroadCastManager.isWelcomeEnabled()){
            if(BroadCastManager.isWelcomeJoinFirst() && isFirstJoin) {
                MessageManager.broadcastMessage(player,BroadCastManager.getWelcomeMessage());
                return;
            }
            MessageManager.broadcastMessage(player,BroadCastManager.getWelcomeMessage());
        }
    }

    @Subscribe(priority = 0)
    public void onDisConnect(DisconnectEvent evt){
        Player player = evt.getPlayer();
        UUID uuid = player.getUniqueId();
        // store and clean memory player data
        PlayerManager.removePlayer(player);
        PlayerManager.updatePlayerLeftTime(uuid,System.currentTimeMillis());
        PlayerManager.updatePlayerAttrs(uuid,playerAttrs.get(uuid));
        playerAttrs.remove(uuid);
        // update player tab list
        if(TabListManager.isTabListEnable()) {
            proxyServer.getAllPlayers().forEach(p -> p.getTabList().removeEntry(uuid));
        }

        // proxy leave message broadcast
        MessageManager.broadcastProxyMessage(player,false);
    }


}

