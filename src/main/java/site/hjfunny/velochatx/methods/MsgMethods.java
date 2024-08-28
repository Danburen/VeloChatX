package site.hjfunny.velochatx.methods;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.waterwood.common.PluginBase;
import me.waterwood.common.Colors;

public class MsgMethods extends Methods {
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
                return placeValue(PluginBase.getMessage(key,sourcePlayer.getEffectiveLocale().getLanguage()),sourcePlayer);
            }else{
                return Colors.parseColor(placeValue(PluginBase.getMessage(key),sourcePlayer));
            }
        }else{
            return convertServer(PluginBase.getMessage(key),source,target);
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
            return PluginBase.getMessage(key,sourcePlayer.getEffectiveLocale().getLanguage());
        }else{
            return PluginBase.getMessage(key);
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
    public static String ValueToServer(String original,String serverName){
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
}
