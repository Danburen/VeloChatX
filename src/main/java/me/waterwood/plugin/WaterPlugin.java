package me.waterwood.plugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.waterwood.common.Basics;
import me.waterwood.common.Colors;
import me.waterwood.common.PluginBase;
import me.waterwood.config.ConfigProcesser;
import me.waterwood.config.FileConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.Map;


public abstract class WaterPlugin  implements Plugin {
    private static Logger logger ;
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
        if(config.getString("player-locale").equals("message")){
            PluginBase.setLocale(true);
        }else{
            PluginBase.setLocale(false);
        }
    }

    public String sendGetRequest(String urlStr){
        StringBuilder result = new StringBuilder();
        try{
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int requestCode = connection.getResponseCode();
            if(requestCode == HttpURLConnection.HTTP_OK){
                InputStream IS = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(IS));
                String line;
                while((line = reader.readLine())!= null){
                    result.append(line);
                }
                IS.close();
                connection.disconnect();
            }
        }catch(Exception e) {
            logger.warn(PluginBase.getMessage("error-ckupd-message").formatted(urlStr));
        }
        return result.toString();
    }

    /**
     * Checking for update , config  will automatically load.
     * API GET LIKE: https://api.github.com/repos/[NAME]/[REPOSITORIES]/releases/latest
     * @param name your profile name
     * @param repositories your project's repositories
     */
    public void CheckForUpdata(String name,String repositories){
        String url = "https://api.github.com/repos/%s/%s/releases/latest".formatted(name,repositories);
        if(config == null) config = getConfig();
        String latestJSON = sendGetRequest(url);
        if (latestJSON.equals("")){ return;}
        JsonObject jsonObject = JsonParser.parseString(latestJSON).getAsJsonObject();
        String downloadLink = null;
        JsonArray assets = jsonObject.getAsJsonArray("assets");
        for(JsonElement asset : assets){
            downloadLink = asset.getAsJsonObject().get("browser_download_url").getAsString();
            if (downloadLink != null) break;
        }
        String lastVersion = jsonObject.get("tag_name").getAsString();
        String version = getPluginInfo("version");
        double latest = Basics.parseVersion(lastVersion);
        double current = Basics.parseVersion(version);
        if(lastVersion != null){
            if(current >= latest ){
                logger.info(Colors.parseColor(PluginBase.getMessage("latest-version-message")));
            }else{
                logger.info(Colors.parseColor(PluginBase.getMessage("latest-new-found-message").formatted(lastVersion,downloadLink)));
            }
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

    public static String getLang(){
        return Locale.getDefault().getLanguage();
    }
    public static String getPluginInfo(String key){
        return (String)pluginData.get(key);
    }
}
