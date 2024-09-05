package me.waterwood;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.plugin.WaterPlugin;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class VelocityPlugin extends WaterPlugin {
    private static ProxyServer server = null;
    protected VelocityPlugin(Logger logger, ProxyServer server) {
        super(logger);
        this.server = server;
    }
    public static ProxyServer getProxyServer() {
        return server;
    }

    public static List<String> getAllPlayerName(ProxyServer server){
        List<String> players = new ArrayList<>();
        for(Player player: getProxyServer().getAllPlayers()){
            players.add(player.getUsername());
        }
        return players;
    }
    public static List<String> getAllPlayerName(){
        return getAllPlayerName(server);
    }
}
