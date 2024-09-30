package site.hjfunny.velochatx.methods;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.waterwood.hock.LuckPermsHock;
import org.waterwood.common.Colors;
import org.waterwood.plugin.WaterPlugin;
import org.waterwood.plugin.velocity.VelocityPlugin;
import site.hjfunny.velochatx.TabListManager;
import site.hjfunny.velochatx.VeloChatX;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.waterwood.hock.LuckPermsHock.*;


public abstract class Methods extends WaterPlugin {
    private static String chatFormatText;
    private static Map<String,String> serverDisPlayName = null;
    private static ScheduledTask scheduler;
    private static ProxyServer proxyServer;
    public static void load(ProxyServer proxyServer){
        LuckPermsHock.checkApi();
        chatFormatText = getConfigs().getString("chat-format");
        checkFormat();
        if(getConfigs().getBoolean("server-display.enable")){
            serverDisPlayName = getConfigs().getMap("server-display.display");
        }
        Methods.proxyServer = proxyServer;
        TabListManager.init(proxyServer);
        TabListManager.setConstVals();
        if(scheduler!= null) scheduler.cancel();
        if(TabListManager.isTabListEnable()){
            proxyServer.getAllPlayers().forEach(TabListManager::setUpHeadAndFooter);
            scheduler = proxyServer.getScheduler().buildTask(VeloChatX.getInstance(),task->{
                        proxyServer.getAllPlayers().forEach(TabListManager::updateTabList);
                    })
                    .delay(1L, TimeUnit.SECONDS)
                    .repeat(Methods.getConfigs().getInteger("tab-list.interval"), TimeUnit.MILLISECONDS)
                    .schedule();
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
        return placeValue(origin,player).replace("{message}",message);
    }
    private static void replacePlaceholder(StringBuilder builder, String placeholder, String value) {
        int index = 0;
        while ((index = builder.indexOf(placeholder, index)) != -1) {
            builder.replace(index, index + placeholder.length(), value);
            index += value.length(); // 更新索引以继续查找
        }
    }
    public static String placeValue(String origin,Player player,RegisteredServer targetServer){
        StringBuilder out = placePlayerValue(origin,player);
        replacePlaceholder(out, "{proxy}", convertServerName("proxy"));
        replacePlaceholder(out, "{server}", convertServerName(targetServer.getServerInfo().getName()));
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
        replacePlaceholder(out,"{online}",optSC.map(ServerConnection::getServer).map(RegisteredServer::getPlayersConnected)
                .map(Collection::size).map(String::valueOf).orElse("0"));
        replacePlaceholder(out,"{total_online}", String.valueOf(VelocityPlugin.getAllPlayerName().size()));
        return out.toString();
    }
    public static StringBuilder placePlayerValue(String origin, Player player){
        String in = origin.toLowerCase();
        StringBuilder out = new StringBuilder(in.matches(".*%[^%]+%.*") ? in.replaceAll("%([^%]+)%", "{$1}") : in);
        String playerName = player.getUsername();
        String prefix = getSafeString(getPlayerPrefix(playerName));
        String suffix = getSafeString(getPlayersuffix(playerName));
        String groupDisplayName =getSafeString(getPlayerGroupDisplay(playerName));

        replacePlaceholder(out, "{player}", playerName);
        replacePlaceholder(out, "{prefix}", prefix);
        replacePlaceholder(out, "{suffix}", suffix);
        replacePlaceholder(out, "{group}", groupDisplayName);
        replacePlaceholder(out, "{ping}", String.valueOf(player.getPing()));
        replacePlaceholder(out, "&", "§");

        return out;
    }
    private static String getSafeString(String origin) {
        return origin == null ? "" : origin;
    }

    public static String convertServerName(String serverName){
        if(serverDisPlayName == null){
            return serverName;
        }else{
            return serverDisPlayName.getOrDefault(serverName, serverName);
        }
    }

    public static List<String> getAllPlayer(CommandSource source){
        List<String> players =  VelocityPlugin.getAllPlayerName();
        if(source instanceof Player player){
            players.remove(player.getUsername());
        }
        return players;
    }
    public static void useDefaultFormatChat(){
       chatFormatText = "{Server}{Player} §7:§r {Message}";
    }

}
