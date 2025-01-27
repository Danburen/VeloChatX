package me.waterwood.velochatx.events;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabListEntry;
import me.waterwood.velochatx.manager.TabListManager;
import me.waterwood.velochatx.VeloChatX;
import me.waterwood.velochatx.methods.Methods;
import net.kyori.adventure.text.Component;
import org.waterwood.plugin.WaterPlugin;

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
        TabListManager.setUpHeadAndFooter(player,evt.getServer());
        proxyServer.getAllPlayers().forEach(p ->{
            tabListAddPlayer(player,p);
            tabListAddPlayer(p,player);
        });
    }
    @Subscribe
    public void onPlayerDisConnect(DisconnectEvent evt){
        if(!TabListManager.isTabListEnable()) return;
        final Player player = evt.getPlayer();
        proxyServer.getAllPlayers().forEach(p -> p.getTabList().removeEntry(player.getUniqueId()));
    }

    public void tabListAddPlayer(Player player,Player targetPlayer){
        targetPlayer.getTabList().addEntry(TabListEntry.builder()
                .profile(player.getGameProfile())
                .displayName(Component.text(Methods.placeValue(TabListManager.getTabListFormat(),player)))
                .tabList(targetPlayer.getTabList()).build());
    }
}
