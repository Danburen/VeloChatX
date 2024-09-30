package site.hjfunny.velochatx;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import site.hjfunny.velochatx.methods.Methods;

import java.util.HashMap;
import java.util.Map;

public class TabListManager extends Methods {
    private static final Map<Player,ScheduledTask> playerTasks = new HashMap<>();
    private static boolean TAB_LIST_ENABLE = getConfigs().getBoolean("tab-list.enable");
    private static String TAB_LIST_FORMAT = getConfigs().getString("tab-list.format");
    private static String HEADER_FORMAT = getConfigs().getString("tab-list.header");
    private static String FOOTER_FORMAT = getConfigs().getString("tab-list.footer");
    private static ProxyServer proxyServer;
    public static void init(ProxyServer proxyServer){
        TabListManager.proxyServer = proxyServer;
        setConstVals();
    }
    public static void setConstVals(){
        TAB_LIST_FORMAT =  getConfigs().getString("tab-list.format");
        HEADER_FORMAT = getConfigs().getString("tab-list.header");
        FOOTER_FORMAT = getConfigs().getString("tab-list.footer");
        TAB_LIST_ENABLE = getConfigs().getBoolean("tab-list.enable");
    }
    public static void setHeadAndFooter(Player player, String header, String footer){
        player.sendPlayerListHeaderAndFooter(Component.text(header),Component.text(footer));
    }

    public static void setUpHeadAndFooter(Player player){
        player.getTabList().clearHeaderAndFooter();
        String header = placeValue(HEADER_FORMAT,player);
        String footer = placeValue(FOOTER_FORMAT,player);
        setHeadAndFooter(player,header,footer);
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
}
