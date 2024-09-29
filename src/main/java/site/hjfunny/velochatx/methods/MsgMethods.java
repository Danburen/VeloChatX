package site.hjfunny.velochatx.methods;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.waterwood.common.Colors;
import org.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import org.waterwood.plugin.velocity.VelocityPlugin;
import site.hjfunny.velochatx.VeloChatX;

import java.util.Optional;

public class MsgMethods extends Methods {
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
                return placeValue(getMessage(key,sourcePlayer.getEffectiveLocale().getLanguage()),sourcePlayer);
            }else{
                return Colors.parseColor(placeValue(getMessage(key),sourcePlayer));
            }
        }else{
            return convertServer(getMessage(key),source,target);
        }
    }
    public static String convertServer(String original,CommandSource source,CommandSource target){
        String out = original;
        if(source instanceof RegisteredServer){
            RegisteredServer server = (RegisteredServer) source;
            out = ValueToServer(out,server.getServerInfo().getName());
        }else{
            out = ValueToServer(out,"proxy");
        }
        return target instanceof Player ? out : Colors.parseColor(out);
    }

    public static String getSourceMessage(String key,CommandSource source){
        if(source instanceof Player sourcePlayer) {
            return getMessage(key,sourcePlayer.getEffectiveLocale().getLanguage());
        }else{
            return getMessage(key);
        }
    }
    /*
    public static String convert(String original, CommandSource source){
        return convert(original,source,source);
    }
    public static String convert(String original, CommandSource source, CommandSource showWhom){
        String out = original;
        if(source instanceof Player){
            Player player = (Player) source;
            out = placeValue(original,player);
        }else{
            if(source instanceof RegisteredServer){
                RegisteredServer server = (RegisteredServer) source;
                out = ValueToServer(out,server.getServerInfo().getName());
            }else{
                out = ValueToServer(out,"proxy");
            }
        }
        return showWhom instanceof Player ? out : Colors.parseColor(out);
    }*/
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

    public static void serverMessage(String messageSource, Player player, ServerConnectedEvent evt){
        if(getConfigs().getBoolean(messageSource + ".enable")) {
            RegisteredServer connectServer = evt.getServer();
            evt.getPreviousServer().ifPresent(preServer -> {
                preServer.sendMessage(Component.text(placeValue(getConfigs().getString(messageSource +".player-leave-message"),player,preServer)));
            });
            String joinMessage = placeValue(getConfigs().getString(messageSource + ".player-join-message"),player,connectServer);
            connectServer.sendMessage(Component.text(joinMessage));
            if(getConfigs().getBoolean(messageSource + ".log-to-console")){
                WaterPlugin.getLogger().info(Colors.parseColor(joinMessage));
            }
            if(getConfigs().getBoolean(messageSource + ".send-to-all-subServer")){
                proxyServer.getAllServers().forEach(registeredServer ->
                        registeredServer.sendMessage(Component.text(joinMessage))
                );
            }
        }
    }

    public static void serverMessage(String messageSource, Player player){
        //ProxyServer proxyServer = VelocityPlugin.getProxyServer();
        if(getConfigs().getBoolean(messageSource + ".enable")) {
            String messageText = placeValue(getConfigs().getString(messageSource + ".player-join-message"),player);
            if(getConfigs().getBoolean(messageSource + ".log-to-console")){
                getLogger().info(Colors.parseColor(messageText));
            }
            if(getConfigs().getBoolean(messageSource + ".send-to-all-subServer")){
                proxyServer.getAllServers().forEach(registeredServer ->
                        registeredServer.sendMessage(Component.text(messageText))
                );
            }
        }
    }

    public static void serverMessage(String messageSource, Player player, DisconnectEvent evt){
        ProxyServer proxyServer = VelocityPlugin.getProxyServer();
        if(getConfigs().getBoolean(messageSource + ".enable")) {
            String messageText = placeValue(getConfigs().getString(messageSource + ".player-leave-message"),player);
            if(getConfigs().getBoolean(messageSource + ".log-to-console")){
                VeloChatX.getLogger().info(Colors.parseColor(messageText));
            }
            if(getConfigs().getBoolean(messageSource + ".send-to-all-subServer")){
                proxyServer.getAllServers().forEach(registeredServer ->
                        registeredServer.sendMessage(Component.text(messageText))
                );
            }
        }
    }

    public static boolean hasBanWords(String message){
        String[] banWords = getConfigs().getString("ban-words.words").split(",");
        for(String banWord : banWords){
            if(message.contains(banWord)){
                return true;
            }
        }
        return false;
    }
}
