package site.hjfunny.velochatx.events;


import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.crypto.SignedMessage;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.common.PluginBase;
import me.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import site.hjfunny.velochatx.PlayerAttribution;
import site.hjfunny.velochatx.commands.MentionCommand;
import site.hjfunny.velochatx.methods.Methods;
import site.hjfunny.velochatx.VeloChatX;

import java.util.*;

public class PlayerEvents extends PluginBase {
    private final ProxyServer proxyServer = VeloChatX.getInstance().getProxyServer();
    private static Map<String,PlayerAttribution> playerAttrs = new HashMap<>();
    public static Map<String, PlayerAttribution> getPlayerAttrs() {
        return playerAttrs;
    }
    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayChat(PlayerChatEvent evt){
        String message = evt.getMessage();
        Player source = evt.getPlayer();
        proxyServer.getAllPlayers().forEach(player -> {
            if(player.getCurrentServer().get().getServerInfo().getName().equals(source.getCurrentServer().get().getServerInfo().getName())) return;
            if(playerAttrs.get(player.getUsername()).getIgnorePlayers().contains(source.getUsername())){
                return;
            }
            player.sendMessage(Component.text(Methods.placeChatValue(message,source)));
        });
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onConnectServer(ServerConnectedEvent evt){
        Player player = evt.getPlayer();
        playerAttrs.put(player.getUsername(),new PlayerAttribution(new HashSet<>(),new HashSet<>(),true));
        String locale = Objects.requireNonNull(evt.getPlayer().getEffectiveLocale()).getLanguage();
        if(!(WaterPlugin.getConfig().getLoadedLocal().contains(locale))) {
            WaterPlugin.getConfig().loadLocaleMsg(locale);
        }
        if(WaterPlugin.getConfig().getBoolean("join-leave-broadcast.enable")) {
            RegisteredServer connectServer = evt.getServer();
            evt.getPreviousServer().ifPresent(preServer -> {
                Component text = Component.text().content(
                        Methods.placeValue(getMessage("join-leave-broadcast.player-leave-message"),player,preServer)
                ).build();
                preServer.sendMessage(text);
            });
            Component text = Component.text().content(
                    Methods.placeValue(getMessage("join-leave-broadcast.player-join-message"),player,connectServer)
            ).build();
            connectServer.sendMessage(text);
        }

    }
    @Subscribe(order = PostOrder.NORMAL)
    public void onDisConnect(DisconnectEvent evt){
        playerAttrs.remove(evt.getPlayer().getUsername());
    }
}
//    @Subscribe(order = PostOrder.FIRST)
//    public void onLoginIn(LoginEvent evt){
//        try{
//            String locale = evt.getPlayer().getEffectiveLocale().getLanguage();
//            WaterPlugin.getConfig().loadLocaleMsg(locale);
//        }catch (NullPointerException e){
//            WaterPlugin.getLogger().warn("can't get player's language");
//        }
//    }

