package me.waterwood.plugin;

import me.waterwood.config.FileConfiguration;
import me.waterwood.config.YamlConfigProcesser;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;

public abstract class WaterPlugin  implements PluginBase {
    private static Logger logger = null;
    private File file = null;
    protected static FileConfiguration config = null;
    private static String pluginName;
    private static Map<String,Object> pluginData;
    protected WaterPlugin(Logger logger){
        WaterPlugin.logger = logger;
        getPluginInfo();
    }
    public static Logger getLogger(){
        return logger;
    }
    public static Map<String,Object> getPluginData(){
        return pluginData;
    }

    public static String getPluginName(){
        return pluginName;
    }

    public void onDisable(){};

    public static void upgradeConfig(){
        config = new YamlConfigProcesser();
        config.loadConfig();
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
    public void getPluginInfo(){
        Yaml yaml = new Yaml();
        InputStream pluginFis = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        pluginData = yaml.load(pluginFis);
        pluginName = (String) YamlConfigProcesser.get("name", pluginData);
    }
}
