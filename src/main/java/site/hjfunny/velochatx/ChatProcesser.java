package site.hjfunny.velochatx;

import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.api.LuckPermsAPI;
import me.waterwood.config.FileConfiguration;
import me.waterwood.plugin.WaterPlugin;

import java.util.ArrayList;

public abstract class ChatProcesser extends LuckPermsAPI {
    ArrayList<String> chatKeys;
    private static String chatFormatText;
    private static FileConfiguration config = WaterPlugin.getConfig();

    public static void init(){
        chatFormatText = config.getString("chat-format");
        checkFormat();
    }
    public static void checkFormat(){
        String chatFormat = chatFormatText.toLowerCase();
        if (chatFormat.contains("\\{message\\}") && chatFormat.contains("\\{player\\}") && chatFormat.contains("\\{server\\}")){
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
        String serverName = player.getCurrentServer().get().getServerInfo().getName();
        String prefix = nullStrCheck(getPlayerPrefix(playerName));
        String suffix = nullStrCheck(getPlayersuffix(playerName));
        String GroupDisplayName = nullStrCheck(getPlayerGroup(playerName).getDisplayName());
        String message = evt.getMessage();
        out.replaceAll("\\{player\\}",playerName)
                .replaceAll("\\{server\\}",serverName)
                .replaceAll("\\{prefix\\}",prefix)
                .replaceAll("\\{suffix\\}",suffix)
                .replaceAll("\\{group\\}",GroupDisplayName)
                .replaceAll("\\{message\\}",message);
        return out;
    }

    public static String nullStrCheck(String origin){
        if(origin == null){
            return "";
        }
        return origin;
    }

    public static void useDefaultFormatChat(){
       chatFormatText = "[{Server}]{Player}: {Message}";
    }
}
