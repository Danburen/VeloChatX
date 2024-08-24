package me.waterwood.plugin;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.config.FileConfiguration;
import me.waterwood.config.ConfigProcesser;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

public abstract class WaterPlugin  implements PluginBase {
    private static Logger logger = null;
    private static ProxyServer server = null;
    protected static FileConfiguration config = null;
    private static Map<String,Object> pluginData;
    protected WaterPlugin(Logger logger,ProxyServer server){
        WaterPlugin.logger = logger;
        WaterPlugin.server = server;
        loadPluginInfo();
    }

    public static ProxyServer getProxyServer() {
        return server;
    }

    public static Logger getLogger(){
        return logger;
    }
    public static Map<String,Object> getPluginData(){
        return pluginData;
    }


    public void onDisable(){};

    public static void upgradeConfig(String fullFileName){
        config.reloadConfig(fullFileName);
    }

    public static void upgradeConfig(){
        config = new ConfigProcesser();
        config.loadConfig();
    }

    public static FileConfiguration getConfig(){
        if(config == null){
            upgradeConfig();
        }
        return config;
    }

    public void onload(){
        upgradeConfig();
    }
    public void loadPluginInfo(){
        Yaml yaml = new Yaml();
        try(InputStream pluginFis = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            pluginData = yaml.load(pluginFis);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static String getPluginInfo(String key){
        return (String)pluginData.get(key);
    }
    public static List<String> getAllPlayerName(ProxyServer server){
        List<String> players = new ArrayList<>();
        for(Player player: WaterPlugin.getProxyServer().getAllPlayers()){
            players.add(player.getUsername());
        }
        return players;
    }

    public void registerCommand( Object plugin, SimpleCommand command, String name,String... alias){
        CommandManager manager = server.getCommandManager();
        CommandMeta meta = manager.metaBuilder(name).aliases(alias).plugin(this).build();
        manager.register(meta,command);

    }
    public static List<String> getAllPlayerName(){
        return getAllPlayerName(server);
    }
}
