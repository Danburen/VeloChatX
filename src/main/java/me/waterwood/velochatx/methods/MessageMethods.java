package me.waterwood.velochatx.methods;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.velochatx.manager.BroadCastManager;
import me.waterwood.velochatx.manager.PlayerManager;
import org.waterwood.utils.Colors;
import net.kyori.adventure.text.Component;
import org.waterwood.plugin.velocity.VelocityPlugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MessageMethods extends Methods {
    private static final ProxyServer proxyServer = VelocityPlugin.getProxyServer();

    public static String convertString(String original,CommandSource source,CommandSource target){
        if(source instanceof Player sourcePlayer){
            if(target instanceof Player){
                return placeValue(original,sourcePlayer);
            }else{
                return Colors.parseColor(placeValue(original,sourcePlayer));
            }
        }else{
            return convertServer(original,source,target);
        }
    }
    public static String convertMessage(String key,CommandSource source,CommandSource target){
        if(source instanceof Player sourcePlayer){
            if(target instanceof Player){
                return placeValue(getMessage(key, PlayerManager.getPlayerLangCode(sourcePlayer)),sourcePlayer);
            }else{
                return Colors.parseColor(placeValue(getMessage(key),sourcePlayer));
            }
        }else{
            return convertServer(getMessage(key),source,target);
        }
    }

    public static String convertServer(String original,CommandSource source,CommandSource target){
        String out = original;
        if(source instanceof RegisteredServer server){
            out = ValueToServer(out,server.getServerInfo().getName());
        }else{
            out = ValueToServer(out,"proxy");
        }
        return target instanceof Player ? out : Colors.parseColor(out);
    }

    public static String getSourceMessage(String key,CommandSource source){
        if(source instanceof Player sourcePlayer) {
            return getMessage(key, PlayerManager.getPlayerLangCode(sourcePlayer));
        }else{
            return getMessage(key);
        }
    }

    public static String  ValueToServer(String original,String serverName){
        String out = original.toLowerCase();
        if(out.contains("{")){
            String serverDisplay = convertServerName(serverName);
            if(out.contains("{player}")) {
                out = out.replace("{player}", serverDisplay);
                return out.replaceAll("\\{.*?\\}","");
            }else{
                out = out.replaceAll("\\{.*?\\}","");
                out = serverDisplay + out;
            }
        }
        return out;
    }

    /**
     * Broadcast sub server message
     * @param player Trigger player
     * @param targetServer Trigger server
     * @param isJoin join or leave
     */
    public static void broadcastServerMessage(Player player, RegisteredServer targetServer,boolean isJoin){
        if(BroadCastManager.isBroadcastEnabled()) {
            final String message = placeValue(isJoin ? BroadCastManager.getJoinMessage() : BroadCastManager.getLeaveMessage()
                            ,player,targetServer);
            targetServer.sendMessage(Component.text(
                    isJoin ? BroadCastManager.getJoinPrefix() : BroadCastManager.getLeavePrefix() + message));
            if(BroadCastManager.isLogEnable()) {
                getLogger().info(Colors.parseColor(message));
            }
            if(BroadCastManager.isSendToAll()){
                for(RegisteredServer registeredServer : proxyServer.getAllServers()){
                    if(registeredServer.equals(targetServer)){
                        continue;
                    }
                    registeredServer.sendMessage(Component.text(message));
                }
            }else{
                if(BroadCastManager.isBroadcastInChannel()){
                    for(RegisteredServer registeredServer : proxyServer.getAllServers()){
                        if(registeredServer.equals(targetServer)){
                            continue;
                        }
                        if(ChatControl.canCommunicate(targetServer,registeredServer)){
                            registeredServer.sendMessage(Component.text(message));
                        }
                    }
                }
            }
        }
    }
    /**
     * Broadcast Proxy server message
     * @param player Trigger player
     * @param isJoin join or leave
     */
    public static void broadcastProxyMessage(Player player, boolean isJoin){
        if(BroadCastManager.isBroadcastEnabledProxy()) {
            final String message = placeValue(
                    isJoin ? BroadCastManager.getFullJoinProxyMessage() :
                            BroadCastManager.getFullLeaveProxyMessage(),player);
            ProxyServer proxyServer = VelocityPlugin.getProxyServer();
            if(BroadCastManager.isLogProxyEnable()){
                getLogger().info(Colors.parseColor(message));
            }
            if(BroadCastManager.isSendToAllProxy()){
                proxyServer.getAllServers().forEach(registeredServer ->
                        registeredServer.sendMessage(Component.text(message))
                );
            }
        }
    }

    public static boolean hasBanWords(String message){
        Set<String> banWords = new HashSet<>(Arrays.asList(
                getConfigs().getString("ban-words.words", "").split(",")));
        for(String banWord : banWords){
            if(message.contains(banWord)){
                return true;
            }
        }
        return false;
    }
}
