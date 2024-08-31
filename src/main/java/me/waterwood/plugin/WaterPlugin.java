package me.waterwood.plugin;

import me.waterwood.common.PluginBase;
import me.waterwood.config.FileConfiguration;
import me.waterwood.config.ConfigProcesser;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;

public abstract class WaterPlugin  implements Plugin {
    private static Logger logger = null;
    protected static FileConfiguration config = null;
    private static Map<String,Object> pluginData;
    protected WaterPlugin(Logger logger){
        WaterPlugin.logger = logger;
        loadPluginInfo();
    }

    public static Logger getLogger(){
        return logger;
    }
    public static Map<String,Object> getPluginData(){
        return pluginData;
    }


    public void onDisable(){};

    public static void upgradeConfig(String fullFileName){
        config.reloadConfig(fullFileName);
    }

    public static void upgradeConfig(){
        if (config == null) config = new ConfigProcesser();
        config = config.loadConfig();
        if(config.getString("player-locale").equals("locale")){
            PluginBase.setLocale(true);
        }else{
            PluginBase.setLocale(false);
        }
    }

    public static FileConfiguration getConfig(){
        if(config == null){
            upgradeConfig();
        }
        return config;
    }

    public void onload(){
        upgradeConfig();
    }
    public void loadPluginInfo(){
        Yaml yaml = new Yaml();
        try(InputStream pluginFis = getClass().getClassLoader().getResourceAsStream("plugin.yml")) {
            pluginData = yaml.load(pluginFis);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static String getPluginInfo(String key){
        return (String)pluginData.get(key);
    }
}
