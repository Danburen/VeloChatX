package me.waterwood.velochatx.methods;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.waterwood.velochatx.VeloChatX;
import me.waterwood.velochatx.events.PlayerEvents;
import me.waterwood.velochatx.manager.BroadCastManager;
import me.waterwood.velochatx.manager.PlayerManager;
import me.waterwood.velochatx.utils.SubServer;
import org.waterwood.hock.LuckPermsHock;
import org.waterwood.utils.Colors;
import org.waterwood.plugin.WaterPlugin;
import org.waterwood.plugin.velocity.VelocityPlugin;
import me.waterwood.velochatx.manager.TabListManager;

import java.util.*;

import static org.waterwood.hock.LuckPermsHock.*;


public abstract class Methods extends WaterPlugin {
    protected static Map<String,String> serverDisplayName;
    protected static Map<String,String> serverSourceChannelName;
    protected static Map<String, SubServer> serverInfoMap;

    protected static ProxyServer proxyServer;
    protected static Object pluginInstance;
    private static String CHAT_FORMAT;


    public static void load(){
        Methods.proxyServer = VeloChatX.getProxyServer();
        Methods.pluginInstance = VeloChatX.getInstance();
        PlayerEvents.setPlayerAttrs(PlayerManager.getOnlinePlayerAttrs());
        loadChatFormat();

        if(getConfigs().getBoolean("server-display.enable",false)){
            getAndSetServer();
        }
        TabListManager.initialize();
        BroadCastManager.initialize();
        ChatControl.initialize();
    }

    // Get and set server display name
    private static void getAndSetServer(){
        serverDisplayName = getConfigs().getStringMap("server-display.display");
        serverInfoMap = new HashMap<>();
        serverDisplayName.forEach((k, v) -> serverInfoMap.put(k,new SubServer(k,v)));
    }

    public static void loadChatFormat(){
        LuckPermsHock.checkApi();
        CHAT_FORMAT = getConfigs().getString("chat-format","[{Server}]{PlayerName}: {Message}");
        String chatFormat = CHAT_FORMAT.toLowerCase();
        if (chatFormat.contains("{message}") && chatFormat.contains("{player}") && chatFormat.contains("{server}")){
            if (hasLuckPerm()){
                return;
            }else{
                WaterPlugin.getLogger().info(Colors.parseColor(getPluginMessage("no-api-support-message")
                        .formatted("LuckPerms")));
            }
            return;
        }
        getLogger().warning(getPluginMessage("fail-parsing-chat-format-message")
                + "\n" + getPluginMessage("use-default-chat-format-message"));
        useDefaultFormatChat();
    }

    public static String placeChatValue(String message,Player player){
        return placeChatValue(CHAT_FORMAT,message,player);
    }
    public static String placeChatValue(String origin,String message,Player player){
        return placeValue(origin,player).replace("{message}",message);
    }
    private static void replacePlaceholder(StringBuilder builder, String placeholder, String value) {
        int index = 0;
        while ((index = builder.indexOf(placeholder, index)) != -1) {
            builder.replace(index, index + placeholder.length(), value);
            index += value.length();
        }
    }
    public static String placeValue(String origin,Player player,RegisteredServer targetServer){
        StringBuilder out = placePlayerValue(origin,player);
        String serverName =  convertServerName(targetServer.getServerInfo().getName());
        replacePlaceholder(out, "{proxy}", convertServerName("proxy"));
        replacePlaceholder(out, "{server}",serverName);
        replacePlaceholder(out, "{channel}", serverSourceChannelName.getOrDefault(serverName,""));
        replacePlaceholder(out,"{online}", String.valueOf(targetServer.getPlayersConnected().size()));
        replacePlaceholder(out,"{total_online}", String.valueOf(VelocityPlugin.getAllPlayerName().size()));
        return out.toString();
    }
    public static String placeValue(String origin, Player player){
        StringBuilder out = placePlayerValue(origin,player);
        Optional<ServerConnection> optSC = player.getCurrentServer();
        String serverName = optSC.map(ServerConnection::getServerInfo).map(ServerInfo::getName).orElse("Unknown");
        replacePlaceholder(out, "{proxy}", convertServerName("proxy"));
        replacePlaceholder(out, "{server}", convertServerName(serverName));
        replacePlaceholder(out, "{channel}",serverSourceChannelName.getOrDefault(serverName,""));
        replacePlaceholder(out,"{online}",optSC.map(ServerConnection::getServer)
                .map(RegisteredServer::getPlayersConnected)
                .map(Collection::size).map(String::valueOf).orElse("0"));
        replacePlaceholder(out,"{total_online}", String.valueOf(VelocityPlugin.getAllPlayerName().size()));
        return out.toString();
    }

    private static String getSafeString(String origin) {
        return origin == null ? "" : origin;
    }

    public static StringBuilder placePlayerValue(String origin, Player player){
        String in = origin.toLowerCase();
        StringBuilder out = new StringBuilder(in.matches(".*%[^%]+%.*") ? in.replaceAll("%([^%]+)%", "{$1}") : in);
        String playerName = player.getUsername();
        String prefix = getSafeString(getPlayerPrefix(playerName));
        String suffix = getSafeString(getPlayerSuffix(playerName));
        String groupDisplayName =getSafeString(getPlayerGroupDisplay(playerName));

        replacePlaceholder(out, "{player}", playerName);
        replacePlaceholder(out, "{prefix}", prefix);
        replacePlaceholder(out, "{suffix}", suffix);
        replacePlaceholder(out, "{group}", groupDisplayName);
        replacePlaceholder(out, "{ping}", String.valueOf(player.getPing()));
        replacePlaceholder(out, "&", "§");

        return out;
    }

    public static String convertServerName(String serverName){
        if(serverDisplayName == null){
            return serverName;
        }else{
            return serverDisplayName.getOrDefault(serverName, serverName);
        }
    }
    public static void useDefaultFormatChat(){
       CHAT_FORMAT = "{Server}{Player} §7:§r {Message}";
    }

    public static SubServer getSubServer(String serverName){ return serverInfoMap.get(serverName); }

    public static SubServer getSubServer(RegisteredServer registeredServer){
        return getSubServer(registeredServer.getServerInfo().getName());
    }

    public static Map<String,SubServer> getSubServerMap(){
        return serverInfoMap;
    }
}
