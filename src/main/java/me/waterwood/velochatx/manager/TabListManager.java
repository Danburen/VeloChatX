package me.waterwood.velochatx.manager;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.waterwood.velochatx.methods.Methods;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;

public class TabListManager extends Methods {
    private static final Map<Player,ScheduledTask> playerTasks = new HashMap<>();
    private static boolean TAB_LIST_ENABLE;
    private static String TAB_LIST_FORMAT;
    private static String HEADER_FORMAT;
    private static String FOOTER_FORMAT;
    public static void initialize(){
        TAB_LIST_FORMAT =  getConfigs().getString("tab-list.format");
        HEADER_FORMAT = getConfigs().getString("tab-list.header");
        FOOTER_FORMAT = getConfigs().getString("tab-list.footer");
        TAB_LIST_ENABLE = getConfigs().getBoolean("tab-list.enable");
    }

    public static void setUpHeadAndFooter(Player player, RegisteredServer server){
        player.getTabList().clearHeaderAndFooter();
        String header = placeValue(HEADER_FORMAT,player,server);
        String footer = placeValue(FOOTER_FORMAT,player,server);
        player.sendPlayerListHeaderAndFooter(Component.text(header),Component.text(footer));
    }

    public static void putPlayerTask(Player player,ScheduledTask task) {
        playerTasks.put(player, task);
    }
    public static Map<Player,ScheduledTask> getAllPlayerTask(){
        return playerTasks;
    }
    public static void cancelAllTasks(){
        for(Map.Entry<Player,ScheduledTask> entry: playerTasks.entrySet()){
            entry.getValue().cancel();
        }
    }

    public static String getTabListFormat() {
        return TAB_LIST_FORMAT;
    }

    public static String getHeaderFormat() {
        return HEADER_FORMAT;
    }

    public static String getFooterFormat() {
        return FOOTER_FORMAT;
    }

    public static boolean isTabListEnable() {
        return TAB_LIST_ENABLE;
    }

    public static void updateTabList(Player player){
        player.getTabList().getEntries().forEach(tabListEntry -> {
            proxyServer.getPlayer(tabListEntry.getProfile().getId()).ifPresent(p -> tabListEntry.setDisplayName(Component.text(Methods.placeValue(TabListManager.getTabListFormat(),
                    p))));
        });
    }
    public static void updatePlayersTabList(){
        proxyServer.getAllPlayers().forEach(TabListManager::updateTabList);
    }
}
