package me.waterwood.common;

import me.waterwood.config.FileConfiguration;
import me.waterwood.plugin.WaterPlugin;

public abstract class PluginBase {
    private static boolean locale = false;
    public static FileConfiguration config = WaterPlugin.getConfig();
    public static void reloadConfig(){
        config = config.reloadConfig();
    }
    public static String getMessage(String key,String lang) {
        return locale ? WaterPlugin.getConfig().getLocalMessage(key,lang) : getMessage(key);
    }
    public static String getMessage(String key){return WaterPlugin.getConfig().getString(key);}

    public static void setLocale(boolean val){
        locale = val;
    }

}
