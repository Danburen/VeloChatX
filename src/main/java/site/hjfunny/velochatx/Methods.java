package site.hjfunny.velochatx;

import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.api.LuckPermsAPI;
import me.waterwood.common.Basics;
import me.waterwood.config.FileConfiguration;
import me.waterwood.plugin.WaterPlugin;

import java.util.Map;

public abstract class Methods extends LuckPermsAPI {
    private static String chatFormatText;
    private static FileConfiguration config = WaterPlugin.getConfig();
    private static Map<String,String> serverDisPlayName = null;
//    private  static final String DefaultChatFormat = ""
    public static void load(){
        chatFormatText = config.getString("chat-format");
        checkFormat();
        if(config.getBoolean("server-display.enable")){
            serverDisPlayName = config.getMap("server-display.display");
        }
    }

    public static void checkFormat(){
        String chatFormat = chatFormatText.toLowerCase();
        if (chatFormat.contains("{message}") && chatFormat.contains("{player}") && chatFormat.contains("{server}")){
            if (hasLuckPerm()){
                return;
            }else{
                WaterPlugin.getLogger().info(Basics.parseColor(config.getString("no-api-support-message")
                        .formatted("LuckPerms",config.getString("use-default-chat-format-message"))));
                useDefaultFormatChat();
            }
            return;
        }
        WaterPlugin.getLogger().warn(config.getString("fail-parsing-chat-format-message")
                + "\n" + config.getString("use-default-chat-format-message"));
        useDefaultFormatChat();
    }
    public static String placeChatValue(PlayerChatEvent evt){
        return placeChatValue(chatFormatText,evt);
    }
    public static String placeChatValue(String origin,PlayerChatEvent evt){
        String out = placeValue(origin,evt.getPlayer());
        String message = evt.getMessage();
        out = out.replace("{message}",message);
        if(config.getBoolean("log-text.enable")) WaterPlugin.getLogger().info(
                Basics.parseColor(out,config.getBoolean("log-text.convert")));
        return out;
    }

    public static String placeValue(String origin, Player player, RegisteredServer server){
        String out = origin.toLowerCase();
        String playerName = player.getUsername();
        String serverName = server == null ? "UnKnown" : convertServerName(server.getServerInfo().getName());
        String prefix = nullStrCheck(getPlayerPrefix(playerName));
        String suffix = nullStrCheck(getPlayersuffix(playerName));
        String GroupDisplayName = nullStrCheck(getPlayerGroup(playerName).getDisplayName());
        out = out.replace("{player}",playerName)
                .replace("{server}",serverName)
                .replace("{prefix}",prefix)
                .replace("{suffix}",suffix)
                .replace("{group}",GroupDisplayName)
                .replace("&","§");// the mc can't convert "&" code tested sometimes.
        return out;
    }

    public static String placeValue(String origin, Player player){
        return placeValue(origin,player,player.getCurrentServer().map(ServerConnection::getServer).orElse(null));
    }
    public static String nullStrCheck(String origin){
        if(origin == null){
            return "";
        }
        return origin;
    }

    public static String convertServerName(String serverName){
        if(serverDisPlayName == null){
            return serverName;
        }else{
            String displayName = serverDisPlayName.get(serverName);
            return displayName ==null ? serverName:displayName;
        }
    }
    public static void useDefaultFormatChat(){
       chatFormatText = "{Server}{Player} §7:§r {Message}";
    }
}
