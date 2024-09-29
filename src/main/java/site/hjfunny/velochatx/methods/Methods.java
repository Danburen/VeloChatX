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
import org.waterwood.api.LuckPermsAPI;
import org.waterwood.common.Colors;
import org.waterwood.plugin.WaterPlugin;
import org.waterwood.plugin.velocity.VelocityPlugin;
import site.hjfunny.velochatx.VeloChatX;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.waterwood.api.LuckPermsAPI.*;


public abstract class Methods extends WaterPlugin {
    private static String chatFormatText;
    private static Map<String,String> serverDisPlayName = null;
    private static ScheduledTask schedulerTask;
    private static ProxyServer proxyServer;
    private static String TAB_LIST_FORMAT = getConfigs().getString("tab-list.format");
    public static void load(ProxyServer proxyServer){
        LuckPermsAPI.checkApi();
        setUpConst();
        chatFormatText = getConfigs().getString("chat-format");
        checkFormat();
        if(getConfigs().getBoolean("server-display.enable")){
            serverDisPlayName = getConfigs().getMap("server-display.display");
        }
        Methods.proxyServer = proxyServer;
        if(getConfigs().getBoolean("tab-list.enable")){
            updateTabListTask(getConfigs().getInteger("tab-list.interval"),proxyServer);
        }else{
            if(schedulerTask != null) schedulerTask.cancel();
            proxyServer.getAllPlayers().forEach(player ->{
                player.getTabList().clearAll();
                player.getTabList().clearHeaderAndFooter();
            });
        }

    }
    public static void setUpConst(){
        TAB_LIST_FORMAT = getConfigs().getString("tab-list.format");
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
        StringBuilder out = new StringBuilder(origin.toLowerCase());
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
    public static void updateTabListTask(int interval,ProxyServer proxyServer){
        if(schedulerTask != null){
            schedulerTask.cancel();
        }
        schedulerTask = proxyServer.getScheduler().buildTask(VeloChatX.getInstance(),() ->
                proxyServer.getAllPlayers().forEach(Methods::updateTabList))
                .repeat(interval, TimeUnit.MILLISECONDS) .schedule();
    }

    public static void updateTabList(Player player){
        TabList tabList = player.getTabList();
        tabList.clearAll();
        String header = Methods.placeValue(getConfigs().getString("tab-list.header"),player);
        String footer = Methods.placeValue(getConfigs().getString("tab-list.footer"),player);
        proxyServer.getAllServers().forEach( server -> proxyServer.getAllPlayers().forEach(p ->{
                /*if (p.getCurrentServer().map(ServerConnection::getServer)
                                .map(ServerConnection -> ServerConnection.equals(server)).orElse(false)){
                  }*/
            TabListEntry tabListEntry= TabListEntry.builder().displayName(Component.text(
                    Methods.placeValue(TAB_LIST_FORMAT,p)
            )).tabList(tabList).profile(p.getGameProfile()).build();
            tabList.addEntry(tabListEntry);
        }));
        player.sendPlayerListHeaderAndFooter(Component.text(header),Component.text(footer));
    }
}
