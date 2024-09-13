package site.hjfunny.velochatx.events;


import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.waterwood.common.Colors;
import org.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import site.hjfunny.velochatx.PlayerAttribution;
import site.hjfunny.velochatx.methods.Methods;
import site.hjfunny.velochatx.VeloChatX;
import site.hjfunny.velochatx.methods.MsgMethods;

import java.util.*;

public class PlayerEvents extends WaterPlugin {
    private final ProxyServer proxyServer = VeloChatX.getInstance().getProxyServer();
    private static Map<String,PlayerAttribution> playerAttrs = new HashMap<>();
    public static Map<String, PlayerAttribution> getPlayerAttrs() {
        return playerAttrs;
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayChat(PlayerChatEvent evt){
        String message = evt.getMessage();
        Player source = evt.getPlayer();
        if(getConfig().getBoolean("ban-words.enable"))
            if(MsgMethods.hasBanWords(message)) {
                source.sendMessage(Component.text(MsgMethods.getMessage("ban-words-message"),NamedTextColor.RED));
                if(getConfig().getBoolean("ban-words.log-to-console")){
                    getLogger().info(Colors.parseColor(MsgMethods.placeChatValue(MsgMethods.getMessage("ban-words-log-message"),message,source)));
                }
                return;
            }
        String fianlMessage = Methods.placeChatValue(message,source);
        if(getConfig().getBoolean("log-text.enable")) getLogger().info(Colors.parseColor(fianlMessage,getConfig().getBoolean("log-text.convert")));
        proxyServer.getAllPlayers().forEach(player -> {
            if(player.getCurrentServer().get().getServerInfo().getName().equals(source.getCurrentServer().get().getServerInfo().getName())) return;
            if(playerAttrs.get(player.getUsername()).getIgnorePlayers().contains(source.getUsername())){
                return;
            }
                player.sendMessage(Component.text(fianlMessage));
        });
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onConnectServer(ServerConnectedEvent evt){
        Player player = evt.getPlayer();
        String locale;
        try {
            locale = player.getEffectiveLocale().getLanguage();
            loadLocale(locale);
        }catch(NullPointerException e){
            getLogger().info(getPluginMessage("cant-load-message"));
        }finally {
            MsgMethods.serverMessage("join-leave-broadcast",player,evt);
        }

    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onProxyConnect(LoginEvent evt){
        Player player = evt.getPlayer();
        playerAttrs.put(player.getUsername(), new PlayerAttribution(new HashSet<>(), new HashSet<>(), true));
        MsgMethods.serverMessage("join-leave-proxy-broadcast",evt.getPlayer(),evt);
    }
    @Subscribe(order = PostOrder.NORMAL)
    public void onDisConnect(DisconnectEvent evt){
        MsgMethods.serverMessage("join-leave-proxy-broadcast",evt.getPlayer(),evt);
        playerAttrs.remove(evt.getPlayer().getUsername());
    }
}
//    @Subscribe(order = PostOrder.FIRST)
//    public void onLoginIn(LoginEvent evt){
//        try{
//            String locale = evt.getPlayer().getEffectiveLocale().getLanguage();
//            config.loadLocaleMsg(locale);
//        }catch (NullPointerException e){
//            getLogger().warn("can't get player's language");
//        }
//    }

