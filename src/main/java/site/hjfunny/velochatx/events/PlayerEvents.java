package site.hjfunny.velochatx.events;


import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.common.PluginBase;
import me.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import site.hjfunny.velochatx.methods.Methods;
import site.hjfunny.velochatx.VeloChatX;

public class PlayerEvents extends PluginBase {
    private final ProxyServer proxyServer = VeloChatX.getInstance().getProxyServer();
    @Subscribe(order = PostOrder.NORMAL)
    public void onPlayChat(PlayerChatEvent evt){

        final Component text = Component.text().content(Methods.placeChatValue(evt)).build();
        proxyServer.getAllServers().forEach(registeredServer -> {
            if (! evt.getPlayer().getCurrentServer().get().getServerInfo().getName()
                    .equals(registeredServer.getServerInfo().getName())) registeredServer.sendMessage(text);
        });
    }

    @Subscribe(order = PostOrder.NORMAL)
    public void onConnectServer(ServerConnectedEvent evt){
        if(WaterPlugin.getConfig().getBoolean("join-leave-broadcast.enable")) {
            Player player = evt.getPlayer();
            try{
                String locale = evt.getPlayer().getEffectiveLocale().getLanguage();
                WaterPlugin.getConfig().loadLocaleMsg(locale);
            }catch (NullPointerException e){
                WaterPlugin.getLogger().warn("can't get player's language");
            }
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

//    @Subscribe(order = PostOrder.FIRST)
//    public void onLoginIn(LoginEvent evt){
//        try{
//            String locale = evt.getPlayer().getEffectiveLocale().getLanguage();
//            WaterPlugin.getConfig().loadLocaleMsg(locale);
//        }catch (NullPointerException e){
//            WaterPlugin.getLogger().warn("can't get player's language");
//        }
//    }
}
