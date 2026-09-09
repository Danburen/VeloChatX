package org.nitrowater.velochatx.events;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import org.nitrowater.velochatx.domain.event.handler.VCXEventHandler;
import org.nitrowater.velochatx.domain.event.player.PlayerJoinServerEvent;
import org.nitrowater.waterapi.domain.event.player.PlayerLoginEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UPlayerRegistry;
import org.nitrowater.waterapi.domain.model.UServer;

public class PlayerEventManager {

    private final UPlayerRegistry playerCachePool;
    private final VCXEventHandler vcxEventHandler;

    public PlayerEventManager(UPlayerRegistry playerCachePool,
                              VCXEventHandler vcxEventHandler) {
        this.playerCachePool = playerCachePool;
        this.vcxEventHandler = vcxEventHandler;
    }

    @Subscribe(priority = 0)
    public void onPlayChat(PlayerChatEvent evt){
        UPlayer player = playerCachePool.getOrCreate(evt.getPlayer());
        org.nitrowater.waterapi.domain.event.player.PlayerChatEvent waterEvent =
                new org.nitrowater.waterapi.domain.event.player.PlayerChatEvent(
                        player,
                        evt.getMessage(),
                        player.getCurrentServer().map(UServer::getName).orElse("unknown")
                );
        vcxEventHandler.onChat(waterEvent);
    }

    @Subscribe(priority = 1)
    public void onConnectServer(ServerConnectedEvent evt){
        UPlayer player = playerCachePool.getOrCreate(evt.getPlayer());
        PlayerJoinServerEvent event = new PlayerJoinServerEvent(
                player,
                evt.getServer().getServerInfo().getName(),
                evt.getPreviousServer().map(
                        prev -> prev.getServerInfo().getName()
                ).orElse(null)
        );
        vcxEventHandler.onJoinServer(event);
    }
    @Subscribe(priority = 3)
    public void onProxyConnect(LoginEvent evt){
        UPlayer player = playerCachePool.getOrCreate(evt.getPlayer());
        vcxEventHandler.onLogin(new PlayerLoginEvent(
                player
        ));
    }

    @Subscribe(priority = 2)
    public void onDisConnect(DisconnectEvent evt) {
        UPlayer player = playerCachePool.getOrCreate(evt.getPlayer());
        org.nitrowater.waterapi.domain.event.player.PlayerDisconnectEvent disconnectEvent =
                new org.nitrowater.waterapi.domain.event.player.PlayerDisconnectEvent(
                        player,
                        player.getCurrentServer().map(UServer::getName).orElse(null)
                );
        vcxEventHandler.onDisconnect(disconnectEvent);
    }
}
