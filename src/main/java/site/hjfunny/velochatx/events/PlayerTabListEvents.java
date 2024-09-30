package site.hjfunny.velochatx.events;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Async;
import org.waterwood.plugin.WaterPlugin;
import site.hjfunny.velochatx.TabListManager;
import site.hjfunny.velochatx.VeloChatX;
import site.hjfunny.velochatx.methods.Methods;

import java.util.concurrent.TimeUnit;

public class PlayerTabListEvents extends WaterPlugin {
    private  final ProxyServer proxyServer;
    private  final VeloChatX plugin;
    public PlayerTabListEvents(ProxyServer proxyServer, VeloChatX plugin){
        this.proxyServer = proxyServer;
        this.plugin = plugin;
    }
    @Subscribe
    public void onPlayerJoin(ServerConnectedEvent evt){
        if(!TabListManager.isTabListEnable()) return;
        final Player player = evt.getPlayer();
        TabListManager.setUpHeadAndFooter(player);
        proxyServer.getAllPlayers().forEach(p ->{
            tabListAddPlayer(player,p);
            tabListAddPlayer(p,player);
        });

//        if(getConfigs().getBoolean("tab-list.enable")) {
//            TabListManager.putPlayerTask(player, proxyServer.getScheduler().buildTask(plugin, task ->{
//                TabListManager.updateTabList(player);
//            })
//                    .delay(1L, TimeUnit.SECONDS)
//                    .repeat(Methods.getConfigs().getInteger("tab-list.interval"), TimeUnit.MILLISECONDS)
//                    .schedule());
//        }
    }
    @Subscribe
    public void onPlayerDisConnect(DisconnectEvent evt){
        if(!TabListManager.isTabListEnable()) return;
        final Player player = evt.getPlayer();
        proxyServer.getAllPlayers().forEach(p ->{
            p.getTabList().removeEntry(player.getUniqueId());
        });
    }

    public void tabListAddPlayer(Player player,Player targetPlayer){
        targetPlayer.getTabList().addEntry(TabListEntry.builder()
                .profile(player.getGameProfile())
                .displayName(Component.text(Methods.placeValue(TabListManager.getTabListFormat(),player)))
                .tabList(targetPlayer.getTabList()).build());
    }
}
