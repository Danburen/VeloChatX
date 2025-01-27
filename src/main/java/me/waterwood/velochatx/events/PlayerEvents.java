package me.waterwood.velochatx.events;


import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.velochatx.manager.BroadCastManager;
import me.waterwood.velochatx.manager.PlayerManager;
import me.waterwood.velochatx.methods.ChatControl;
import org.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.waterwood.velochatx.utils.PlayerAttribution;
import me.waterwood.velochatx.manager.TabListManager;
import me.waterwood.velochatx.methods.Methods;
import me.waterwood.velochatx.methods.MessageMethods;
import org.waterwood.plugin.velocity.VelocityPlugin;
import org.waterwood.utils.Colors;

import java.util.*;

public class PlayerEvents extends WaterPlugin {
    private final ProxyServer proxyServer = VelocityPlugin.getProxyServer();
    private static final Map<UUID,PlayerAttribution> playerAttrs = new HashMap<>();
    public static void setPlayerAttrs(Map<UUID,PlayerAttribution> playerAttrs) {
        PlayerEvents.playerAttrs.putAll(playerAttrs);
    }
    public static Map<UUID, PlayerAttribution> getPlayerAttrs() {
        return playerAttrs;
    }
    @Subscribe(priority = 0)
    public void onPlayChat(PlayerChatEvent evt){
        String message = evt.getMessage();
        Player source = evt.getPlayer();
        if(ChatControl.isBanWorldEnable()) {
            if (MessageMethods.hasBanWords(message)) {
                source.sendMessage(Component.text(MessageMethods.getMessage("ban-words-message"), NamedTextColor.RED));
                if (getConfigs().getBoolean("ban-words.log-to-console", false)) {
                    getLogger().info(Colors.parseColor(MessageMethods.placeChatValue(MessageMethods.getMessage("ban-words-log-message"), message, source)));
                }
                return;
            }
        }
        String finalMessage = Methods.placeChatValue(message,source);
        if(getConfigs().getBoolean("log-text.enable",false)) getLogger().info(Colors.parseColor(finalMessage,! getConfigs().getBoolean("log-text.convert",true)));
        String sourceServerName = source.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse("");
        proxyServer.getAllPlayers().forEach(player -> {
            String playerServerName = player.getCurrentServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse("");
            //chat offline
            if(playerAttrs.get(player.getUniqueId()).isChatOffLine()) return;
            // same server
            if(playerServerName.equals(sourceServerName)) return;
            // white list
            if(playerAttrs.get(player.getUniqueId()).getIgnorePlayers().contains(source.getUniqueId())) return;
            // same channel communicate
            if(!ChatControl.canCommunicate(playerServerName,sourceServerName) && ! BroadCastManager.isChannelGlobal()) return;
            player.sendMessage(Component.text(finalMessage));
        });
    }
    @Subscribe(priority = 0)
    public void onConnectServer(ServerConnectedEvent evt){
        Player player = evt.getPlayer();
        RegisteredServer server = evt.getServer();
        updateAllPlayerTabListHF();
        if(getConfigs().getBoolean("tab-list.enable",false)) {
            TabListManager.setUpHeadAndFooter(player,server);
            TabListManager.updateTabList(player);
        }

        try {
            loadLocale(PlayerManager.getPlayerLangCode(player));
        }catch(NullPointerException e){
            getLogger().info(getPluginMessage("cant-load-message"));
        }finally {
            evt.getPreviousServer().ifPresent(preServer -> {
                MessageMethods.broadcastServerMessage(player,preServer,false);
            });
            MessageMethods.broadcastServerMessage(player,server,true);
            Methods.getSubServer(server.getServerInfo().getName()).addPlayer(player);
        }

    }
    @Subscribe(priority = 0)
    public void onProxyConnect(LoginEvent evt){
        playerAttrs.put(evt.getPlayer().getUniqueId(), new PlayerAttribution(new HashSet<>(), new HashSet<>()));
        MessageMethods.broadcastProxyMessage(evt.getPlayer(),true);
        PlayerManager.storeOrUpdatePlayer(evt.getPlayer());
    }
    @Subscribe(priority = 0)
    public void onDisConnect(DisconnectEvent evt){
        updateAllPlayerTabListHF();
        Player player = evt.getPlayer();
        MessageMethods.broadcastProxyMessage(player,false);
        playerAttrs.remove(player.getUniqueId());
        PlayerManager.removePlayer(player);
    }

    private void updateAllPlayerTabListHF(){
        proxyServer.getAllServers().forEach(
                registeredServer -> registeredServer.getPlayersConnected().forEach(
                        player -> TabListManager.setUpHeadAndFooter(player,registeredServer)
                )
        );
    }
}

