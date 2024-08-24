package site.hjfunny.velochatx;

import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.api.LuckPermsAPI;
import me.waterwood.common.basics;
import me.waterwood.config.FileConfiguration;
import me.waterwood.plugin.WaterPlugin;

import java.awt.*;
import java.util.Map;

public abstract class ChatProcesser extends LuckPermsAPI {
    private static String chatFormatText;
    private static FileConfiguration config = WaterPlugin.getConfig();
    private static Map<String,String> serverDisPlayName = null;
    public static void load(){
        chatFormatText = config.getString("chat-format");
        checkFormat();
        if(config.getBoolean("server-display.enable")){
            serverDisPlayName = config.getMap("server-display.display");
        }
    }

    public static void reLoad(){
        config = WaterPlugin.getConfig();
        load();
    }
    public static void checkFormat(){
        String chatFormat = chatFormatText.toLowerCase();
        if (chatFormat.contains("{message}") && chatFormat.contains("{player}") && chatFormat.contains("{server}")){
            if (hasLuckPerm()){
                return;
            }else{
                WaterPlugin.getLogger().warn("LuckPerms haven't loaded yet. Now use default chat format");
                useDefaultFormatChat();
            }
            return;
        }
        WaterPlugin.getLogger().warn("Fail to parse chat format please check the config. Now use default chat format");
        useDefaultFormatChat();
    }
    public static String placeChat(PlayerChatEvent evt){
        return placeChat(chatFormatText,evt);
    }
    public static String placeChat(String origin,PlayerChatEvent evt){
        String out = origin.toLowerCase();
        Player player = evt.getPlayer();
        String playerName = player.getUsername();
        String serverName = convertServerName( player.getCurrentServer().get().getServerInfo().getName() );
        String prefix = nullStrCheck(getPlayerPrefix(playerName));
        String suffix = nullStrCheck(getPlayersuffix(playerName));
        String GroupDisplayName = nullStrCheck(getPlayerGroup(playerName).getDisplayName());
        String message = evt.getMessage();
        out = out.replace("{player}",playerName)
                .replace("{server}",serverName)
                .replace("{prefix}",prefix)
                .replace("{suffix}",suffix)
                .replace("{group}",GroupDisplayName)
                .replace("{message}",message);
        out = out.replace("&","§");// the mc can't convert "&" code tested sometimes.
        if(config.getBoolean("log-text.enable")) WaterPlugin.getLogger().info(basics.parseColor(out,!config.getBoolean("log-text.remove"),config.getBoolean("log-text.convert")));
        return out;
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
       chatFormatText = "[{Server}]{Player}: {Message}";
    }
}
