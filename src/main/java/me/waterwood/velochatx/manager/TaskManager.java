package me.waterwood.velochatx.manager;

import com.velocitypowered.api.scheduler.ScheduledTask;
import me.waterwood.velochatx.VeloChatX;
import me.waterwood.velochatx.methods.Methods;
import net.kyori.adventure.text.Component;
import org.waterwood.plugin.velocity.util.ScheduledManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A Class to holder Tasks
 * @since 1.4.0
 * @author Danburen
 */
public class TaskManager extends Methods {
    private static ScheduledTask scheduler;
    public static void initTask(){
        ScheduledManager scheduledManager = new ScheduledManager(proxyServer,pluginInstance);
        scheduledManager.cancelAllTasks();

        /* from methods.class
        if(scheduler!= null) scheduler.cancel();
        if(TabListManager.isTabListEnable()){
            scheduler = proxyServer.getScheduler().buildTask(VeloChatX.getInstance(),
                            task-> proxyServer.getAllPlayers().forEach(TabListManager::updateTabList))
                    .delay(1L, TimeUnit.SECONDS)
                    .repeat(Methods.getConfigs().getInteger("tab-list.interval",1000), TimeUnit.MILLISECONDS)
                    .schedule();
        }*/

        // Player's tab list update task
        if(TabListManager.isTabListEnable()) tabListUpdateTask(scheduledManager);
        // Broadcast task
        if(BroadCastManager.isEnabled()) broadcastMessageTask(scheduledManager);


    }

    private static void tabListUpdateTask(ScheduledManager scheduledManager){
        scheduledManager.addRepeatTask("update-tab-list",
                TabListManager::updatePlayersTabList,
                getConfigs().getLong("tab-list.interval",1000L),
                TimeUnit.MILLISECONDS);
    }
    /**
     * Broadcast Single message to the player which in <b>target server</b>
     * <p></p>
     * @param message <b>raw</b> message to broadcast
     * @param targetServers target servers which allow to broadcast
     */
    public static void broadcastMessage(String message, Set<String> targetServers){
        proxyServer.getAllPlayers().forEach(player -> {
            String playerServerName = player.getCurrentServer()
                    .map(serverConnection -> serverConnection.getServerInfo().getName())
                    .orElse("");
            if(targetServers.contains(playerServerName)){
                player.sendMessage(Component.text(message));
            }
        });
    }

    private static void broadcastMessageTask(ScheduledManager scheduledManager){
        List<String> messages = new ArrayList<>();
        scheduledManager.addRepeatTask("broadcast",
                ()->{

                },
                BroadCastManager.getBroadcastConfigs().getLong("interval",60L),
                TimeUnit.SECONDS);
    }
}
