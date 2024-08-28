package site.hjfunny.velochatx.methods;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.VelocityPlugin;
import me.waterwood.api.LuckPermsAPI;
import me.waterwood.common.Colors;
import me.waterwood.config.FileConfiguration;
import me.waterwood.plugin.WaterPlugin;

import java.util.List;
import java.util.Map;

public abstract class Methods extends LuckPermsAPI {
    private static String chatFormatText;
    private static FileConfiguration config = WaterPlugin.getConfig();
    private static Map<String,String> serverDisPlayName = null;
//    private  static final String DefaultChatFormat = ""
    public static void load(){
        checkApi();
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
                WaterPlugin.getLogger().info(Colors.parseColor(config.getString("no-api-support-message")
                        .formatted("LuckPerms")));
            }
            return;
        }
        WaterPlugin.getLogger().warn(config.getString("fail-parsing-chat-format-message")
                + "\n" + config.getString("use-default-chat-format-message"));
        useDefaultFormatChat();
    }
    public static String placeChatValue(String message,Player player){
        return placeChatValue(chatFormatText,message,player);
    }
    public static String placeChatValue(String origin,String message,Player player){
        String out = placeValue(origin,player);
        out = out.replace("{message}",message);
        if(config.getBoolean("log-text.enable")) WaterPlugin.getLogger().info(
                Colors.parseColor(out,config.getBoolean("log-text.convert")));
        return out;
    }

    public static String placeValue(String origin, Player player, RegisteredServer server){
        String out = origin.toLowerCase();
        String playerName = player.getUsername();
        String serverName = server == null ? "UnKnown" : convertServerName(server.getServerInfo().getName());
        String prefix = nullStrCheck(getPlayerPrefix(playerName));
        String suffix = nullStrCheck(getPlayersuffix(playerName));
        String GroupDisplayName = nullStrCheck(getPlayerGroupDisplay(playerName));
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

    public static List<String> getAllPlayer(CommandSource source){
        List<String> players = VelocityPlugin.getAllPlayerName();
        if(source instanceof Player){
            Player player = (Player) source;
            players.remove(player.getUsername());
        }
        return players;
    }
    public static void useDefaultFormatChat(){
       chatFormatText = "{Server}{Player} §7:§r {Message}";
    }
}
