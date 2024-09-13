package org.waterwood.plugin;

import org.waterwood.common.Colors;
import org.waterwood.io.FileConfigProcess;
import org.waterwood.io.web.Updater;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;


public abstract class WaterPlugin  implements Plugin {
    private static Logger logger;
    private static FileConfigProcess config = new FileConfigProcess();
    private static FileConfigProcess pluginMessages = new FileConfigProcess();
    private static Map<String,FileConfigProcess>  messages = new HashMap<>();
    private static  FileConfigProcess pluginData;
    private static boolean locale = false;
    protected WaterPlugin(){
        if(pluginData == null){
            try {
                pluginData = new FileConfigProcess();
                pluginData.loadSource("plugin", "yml");
            }catch (IOException e){
                logger.warning("Plugin not founded");
            }
        }
        if (logger == null){ logger = Logger.getLogger(getPluginInfo("name"));}
    }
    public static Logger getLogger(){
        return logger;
    }

    public void LogMsg(String message){
        logger.info(Colors.parseColor(message));
    }

    public static FileConfigProcess getConfig(){
        return config;
    }
    public static String getPluginMessage(String path){
        return pluginMessages.getString(path);
    }
    public String getPluginName(){
        return (String) getPluginData("name");
    }
    public String getDefaultFilePath(String filePath){
        return config.getPluginFilePath(getPluginName(), filePath);
    }


    public static String getPluginData(String path){
        return pluginData.getString(path);
    }


    public void onDisable(){};
    @Override
    public void loadConfig(){
        String lang = Locale.getDefault().getLanguage();
        try {
            config.createFile("config",getPluginName());
            config.createFile("message",getPluginName());
            config.loadFile(getDefaultFilePath("config.yml"));
            locale = config.getString("player-locale").equals("locale");
            messages.put(lang,new FileConfigProcess().loadFile(getDefaultFilePath("message.yml")));
            pluginMessages.loadSource("locale/" + lang , "properties");
        }catch(Exception e){
            getLogger().warning("Error when load config file, missing lang:" + lang + "\nUsing default lang en");
            loadDefaultSource("en");
            e.printStackTrace();
        }
    }

    @Override
    public void reloadConfig(){
        String lang = config.getString("locale");
        try {
            config.loadFile(getDefaultFilePath("config.yml"));
            locale = config.getString("player-locale").equals("locale");
            pluginMessages.loadSource("locale/" + lang , "properties");
        }catch(Exception e){
            logger.warning("Error when load config file, missing lang:" + lang + "\nUsing default lang en");
            loadDefaultSource("en");
        }
    }

    public void reloadConfig(String dataName) throws IOException{
        switch (dataName){
            case "config" : config.loadFile(getDefaultFilePath("config.yml"));break;
            case "message": pluginMessages.loadSource("locale/" + config.getString("locale"),"properties");break;
            default : reloadConfig();
        };
    }

    @Override
    public final String getDefaultSourcePath(String source, String extension, String lang){
        return source + "/" + lang +"."+ extension;
    }

    @Override
    public void loadDefaultSource(String lang){
        try {
            config.loadSource(getDefaultSourcePath("config","yml","en"));
            pluginMessages.loadSource("locale/en.properties");
        }catch (IOException e){
            getLogger().warning("Source not founded!");
        }
    }

    public static String getLang(){
        return Locale.getDefault().getLanguage();
    }
    @Override
    public void checkUpdate(boolean download, String author, String repositories){
        getLogger().info(getPluginMessage("check-update-message"));
        Map<String,Object> updateInfo = Updater.CheckForUpdata(author, repositories, Updater.parseVersion(getPluginData("version")));
        if(updateInfo == null){
            getLogger().warning(getPluginMessage("error-ckupd-message"));
            return;
        }
        if((boolean)updateInfo.get("hasNewVersion")){
            LogMsg(getPluginMessage("new-version-founded-message").formatted(updateInfo.get("latestVersion"),
                    updateInfo.get("downloadLink")));
        }else{
            LogMsg(getPluginMessage("latest-version-message"));
        }
        if(download){

        }
    }
    public void loadLocale(String lang){
        if(messages.containsKey(lang)) return;
        try {
            messages.put(lang,new FileConfigProcess().loadFile("message/" + lang + ".yml"));
            logger.info(getPluginMessage("successfully-load-local-message").formatted(lang));
        }catch (IOException e){
            logger.warning(pluginMessages.getString("fail-find-local-message").formatted(lang));
        }
    }
    public static String getMessage(String key,String lang) {
        return locale ? messages.get(lang).getString(key) : getMessage(key);
    }
    public static String getMessage(String key){return messages.get(Locale.getDefault().getLanguage()).getString(key);}
    public static String getPluginInfo(String key){
        return (String)pluginData.get(key);
    }
}