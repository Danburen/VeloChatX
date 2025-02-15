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
    public static void setPlayerAttrs(Map<UUID,PlayerAttribution> playerAttrs) {
        if(playerAttrs == null) playerAttrs = new HashMap<>();
        PlayerEvents.playerAttrs.putAll(playerAttrs);
    }
    public static Map<UUID, PlayerAttribution> getPlayerAttrs() {
        return playerAttrs;
    }

    @Subscribe(priority = 0)
    public void onPlayChat(PlayerChatEvent evt){
        String message = evt.getMessage();
        Player source = evt.getPlayer();
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
        if(getConfigs().getBoolean("log-text.enable",false)) getLogger().info(Colors.parseColor(finalMessage,! getConfigs().getBoolean("log-text.convert",true)));
        String sourceServerName = source.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse("unknown");
        proxyServer.getAllPlayers().forEach(player -> {
            String playerServerName = player.getCurrentServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse("unknown");
            //chat offline
            if(playerAttrs.get(player.getUniqueId()).isChatOffLine()) return;
            // same server
            if(playerServerName.equals(sourceServerName)) return;
            // white list
            if(playerAttrs.get(player.getUniqueId()).getIgnorePlayers().contains(source.getUniqueId())) return;
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
        // add player data to memory
        playerAttrs.put(evt.getPlayer().getUniqueId(), new PlayerAttribution(new HashSet<>(), new HashSet<>()));
        boolean isPlayerExist = PlayerManager.storeOrUpdatePlayer(evt.getPlayer());

        // broadcast proxy join message(multiple player)
        MessageManager.broadcastProxyMessage(evt.getPlayer(),true);
        //welcome message broadcast(singal player)
        if(BroadCastManager.isWelcomeEnabled()){
            if(BroadCastManager.isWelcomeJoinFirst() && ! isPlayerExist) {
                MessageManager.broadcastMessage(evt.getPlayer(),BroadCastManager.getWelcomeMessage());
                return;
            }
            MessageManager.broadcastMessage(evt.getPlayer(),BroadCastManager.getWelcomeMessage());
        }
    }

    @Subscribe(priority = 0)
    public void onDisConnect(DisconnectEvent evt){
        Player player = evt.getPlayer();
        // store and clean memory player data
        playerAttrs.remove(player.getUniqueId());
        PlayerManager.removePlayer(player);
        PlayerManager.updatePlayerLeftTime(player,System.currentTimeMillis());
        // update player tab list
        if(TabListManager.isTabListEnable()) {
            proxyServer.getAllPlayers().forEach(p -> p.getTabList().removeEntry(player.getUniqueId()));
        }

        // proxy leave message broadcast
        MessageManager.broadcastProxyMessage(player,false);
    }


}

