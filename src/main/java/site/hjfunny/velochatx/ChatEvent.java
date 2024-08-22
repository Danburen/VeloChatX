package site.hjfunny.velochatx;


import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.api.LuckPermsAPI;
import net.kyori.adventure.text.Component;

public class ChatEvent {
    private final ProxyServer proxyServer = VeloChatX.getInstance().getProxyServer();
    @Subscribe(order = PostOrder.EARLY)
    public void onPlayChat(PlayerChatEvent evt){

        final Component text = Component.text().content(ChatProcesser.placeChat(evt)).build();
        proxyServer.getAllServers().forEach(registeredServer -> {
            if (! evt.getPlayer().getCurrentServer().get().getServerInfo().getName()
                    .equals(registeredServer.getServerInfo().getName())) registeredServer.sendMessage(text);
        });
    }
}
