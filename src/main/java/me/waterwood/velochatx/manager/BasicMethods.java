package me.waterwood.velochatx.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.velochatx.VeloChatX;
import me.waterwood.velochatx.utils.Channel;
import me.waterwood.velochatx.utils.PlayerAttribution;
import me.waterwood.velochatx.utils.SubServer;
import org.waterwood.plugin.velocity.util.MethodBase;
import org.waterwood.plugin.velocity.VelocityPlugin;

import java.util.*;

import static org.waterwood.hock.LuckPermsHock.*;


public abstract class BasicMethods extends MethodBase {
    protected static Map<String,String> serverDisplayName;
    protected static Map<String, Channel> serverSourceChannel;
    protected static Map<String, SubServer> serverInfoMap;

    protected static ProxyServer proxyServer;
    protected static Object pluginInstance;


    protected static String PROXY_DISPLAY;
    static {
        serverDisplayName = new HashMap<>();
        serverSourceChannel = new HashMap<>();
        serverInfoMap = new HashMap<>();
    }


    public static void load(){
        BasicMethods.proxyServer = VeloChatX.getProxyServer();
        BasicMethods.pluginInstance = VeloChatX.getInstance();
        if(getConfigs().getBoolean("server-display.enable",false)){
            getAndSetServer();
        }
        LogManager.init();
        TabListManager.initialize();
        BroadCastManager.initialize();
        PlayerManager.initialize();
        ChatManager.initialize();
        TaskManager.initTask();
    }

    // Get and set server display name
    private static void getAndSetServer(){
        Map<String,String> displayMap = getConfigs().get("server-display.display",new HashMap<String,String>());
        serverInfoMap.put("unknown",new SubServer("unknown","UNKNOWN",null));
        for(Map.Entry<String,String> entry : displayMap.entrySet()){
            String k = entry.getKey();String v = entry.getValue();
            // display contains proxy key then band it to proxy display.
            if(k.equalsIgnoreCase("proxy")){
                PROXY_DISPLAY = v;
                continue;
            }

            // sub server class correspond with register server in order to ensure the integrity of server data.
            proxyServer.getAllServers().forEach(regServer -> {
                if(regServer.getServerInfo().getName().equals(k)){
                    serverInfoMap.put(k, new SubServer(k, v, regServer));
                    serverDisplayName.put(k,v);
                }
            });
        }
        if(PROXY_DISPLAY == null) PROXY_DISPLAY = getConfigs().getString("server-display.proxy","[Proxy]");
    }



    private static void replacePlaceholder(StringBuilder builder, String placeholder, String value) {
        int index = 0;
        while ((index = builder.indexOf(placeholder, index)) != -1) {
            builder.replace(index, index + placeholder.length(), value);
            index += value.length();
        }
    }
    public static String placeValue(String origin,Player player,RegisteredServer targetServer){
        return placeServerValue(placePlayerValue(origin,player),targetServer.getServerInfo().getName()).toString();
    }

    /**
     * Place value with unsafe getting player's server
     * @param origin original string
     * @param player value to place from player
     * @return value placed
     */
    public static String placeValue(String origin, Player player){
        StringBuilder out = placePlayerValue(origin,player);
        String serverName = player.getCurrentServer().map(sc ->
               sc.getServerInfo().getName()).orElse("unknown");
        placeServerValue(out, serverName);
        return out.toString();
    }

    /**
     * Place server placeholder value
     * @param original string builder of original
     * @param serverName target server name
     * @return value placed string builder
     */
    public static StringBuilder placeServerValue(StringBuilder original,String serverName){
        replacePlaceholder(original, "{channel}", Optional.ofNullable(serverSourceChannel.get(serverName)).map(Channel::getChannelDisplayName).orElse(""));
        replacePlaceholder(original, "{server}",serverDisplayName.getOrDefault(serverName, serverName));
        replacePlaceholder(original, "{proxy}", PROXY_DISPLAY);
        replacePlaceholder(original,"{online}", String.valueOf(
                Optional.ofNullable(serverInfoMap.get( serverName )).map(SubServer::getPlayerCount).orElse(0)));
        replacePlaceholder(original,"{total_online}", String.valueOf(VelocityPlugin.getPlayerCount()));
        return original;
    }

    public static StringBuilder placePlayerValue(String origin, Player player){
        String in = origin.toLowerCase();
        StringBuilder out = new StringBuilder(in.matches(".*%[^%]+%.*") ? in.replaceAll("%([^%]+)%", "{$1}") : in);
        String playerName = player.getUsername();
        String prefix = getPlayerPrefix(playerName);
        String suffix = getPlayerSuffix(playerName);
        String groupDisplayName = getPlayerGroupDisplay(playerName);

        replacePlaceholder(out, "{player}", playerName);
        replacePlaceholder(out, "{prefix}", prefix);
        replacePlaceholder(out, "{suffix}", suffix);
        replacePlaceholder(out, "{group}", groupDisplayName);
        replacePlaceholder(out, "{ping}", String.valueOf(player.getPing()));
        replacePlaceholder(out, "&", "§");

        return out;
    }

    public static SubServer getSubServer(String serverName){
        return Optional.of(serverInfoMap.get(serverName)).orElse(serverInfoMap.get("unknown"));
    }

    public static SubServer getSubServer(RegisteredServer registeredServer){
        return getSubServer(registeredServer.getServerInfo().getName());
    }

    public static Map<String,SubServer> getSubServerMap(){
        return serverInfoMap;
    }
}
