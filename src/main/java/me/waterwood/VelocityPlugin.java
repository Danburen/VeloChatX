package me.waterwood;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.waterwood.plugin.WaterPlugin;

import java.util.ArrayList;
import java.util.List;

public class VelocityPlugin extends WaterPlugin {
    private static ProxyServer server = null;
    protected VelocityPlugin(ProxyServer server) {
        VelocityPlugin.server = server;
        this.initialization();
    }
    public static ProxyServer getProxyServer() {
        return server;
    }

    public static List<String> getAllPlayerName(){
        List<String> players = new ArrayList<>();
        for(Player player: server.getAllPlayers()){
            players.add(player.getUsername());
        }
        return players;
    }

}
