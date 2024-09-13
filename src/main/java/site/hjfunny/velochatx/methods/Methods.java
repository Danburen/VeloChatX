package site.hjfunny.velochatx.methods;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.VelocityPlugin;
import org.waterwood.api.LuckPermsAPI;
import org.waterwood.common.Colors;
import org.waterwood.plugin.WaterPlugin;

import java.util.List;
import java.util.Map;

import static org.waterwood.api.LuckPermsAPI.*;

public abstract class Methods extends WaterPlugin {
    private static String chatFormatText;
    private static Map<String,String> serverDisPlayName = null;
//    private  static final String DefaultChatFormat = ""

    public static void load(){
        LuckPermsAPI.checkApi();
        chatFormatText = getConfig().getString("chat-format");
        checkFormat();
        if(getConfig().getBoolean("server-display.enable")){
            serverDisPlayName = getConfig().getMap("server-display.display");
        }
    }

    public static void checkFormat(){
        String chatFormat = chatFormatText.toLowerCase();
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
        return placeChatValue(chatFormatText,message,player);
    }
    public static String placeChatValue(String origin,String message,Player player){
        String out = placeValue(origin,player);
        out = out.replace("{message}",message);

        return out;
    }

    public static String placeValue(String origin, Player player, ProxyServer proxyServer){
        String out = origin.toLowerCase();
        String playerName = player.getUsername();
        String serverName = getConfig().getString("server-display.display.proxy");
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
