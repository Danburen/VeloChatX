package me.waterwood.config;

import me.waterwood.common.Basics;
import me.waterwood.plugin.WaterPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigProcesser extends FileConfiguration{
    ConfigProcesser config = null;
    Yaml yaml = new Yaml(getDumperOptions());
    private static Map<String,Object> configData;
    public ConfigProcesser(){

    }
    @Override
    public FileConfiguration loadConfig(){
        if (config == null) createConfigFiles();
        this.config = new ConfigProcesser();
        try {
            configData = loadFile(getPluginFilePath("config.yml"));
            configData.putAll(loadFile(getPluginFilePath("message.yml")));
            loadPluginMessages((String)configData.get("locale"));
        }catch(IOException e){
            loadDefaultSource(true);
            WaterPlugin.getLogger().warn(config.getString("use-default-config-message"));
        }
        return config;
    }
    public void loadPluginMessages(String lang){
        String langPath = "pluginMessages" + "/" + lang + ".properties";
        if(isResourceExist(langPath)){
            configData.putAll(loadPropSource(langPath));
            WaterPlugin.getLogger().info(Basics.parseColor(getString("lang-file-use-message").formatted(lang)));
        }else{
            WaterPlugin.getLogger().warn("(Unsupported/Wrong/Missing lang code)Can't find plugin message lang source : " + lang);
            WaterPlugin.getLogger().info("now loading default plugin message lang : en" );
            configData.putAll(loadPropSource("pluginMessages.properties"));
        }
    }
    @Override
    public FileConfiguration reloadConfig(String resourcePath){
        if(resourcePath.equalsIgnoreCase("all")){
            return loadConfig();
        }
        try {
            if(resourcePath.substring(resourcePath.lastIndexOf(".") + 1).equals("yml")) {
                configData.putAll(loadFile(getPluginFilePath(resourcePath)));
            }else{
                configData.putAll(loadPropSource(resourcePath));
            }
        }catch(IOException e){
            WaterPlugin.getLogger().warn(String.format("Can't reload %s file config,Use default config!",resourcePath));
            loadDefaultSource(true);
        }
        loadPluginMessages((String)configData.get("locale"));
        return config;
    }

    public Map<String,Object> loadPropSource(String source){
        if(isResourceExist(source)){
            Properties prop = new Properties();
            try(InputStream IS = getClass().getResourceAsStream("/" +source)){
                if(IS != null) {
                    prop.load(new InputStreamReader(IS, StandardCharsets.UTF_8));
                    Map<String, Object> out = new HashMap<>();
                    for (Map.Entry<Object, Object> entry : prop.entrySet()) {
                        out.put((String) entry.getKey(), entry.getValue());
                    }
                    return out;
                }else{
                    WaterPlugin.getLogger().warn("Can't found source : " + source);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    @Override
    public Map<String,Object> loadSource(String source){
        try(InputStream IS = getClass().getResourceAsStream("/" + source)){
            return yaml.load(IS);
        }catch (IOException e){
            e.printStackTrace();
            WaterPlugin.getLogger().warn("source not founded" + source);
        }
        return null;
    }
    @Override
    public Map<String,Object> loadFile(String filePath) throws IOException{
        File file = new File(filePath);
        try(InputStream FIS = new FileInputStream(file)){
            return yaml.load(FIS);
        }catch (IOException e){
            if(e instanceof FileNotFoundException) {
                WaterPlugin.getLogger().warn("Can't found" + file.getName());
            }else{
                WaterPlugin.getLogger().warn("Can't load" + file.getName() + "please check the file is not occupied");
            }
            e.printStackTrace();
            throw new IOException(e);
        }
    }
    @Override
    public final Object get(String path){
        return get(path,configData);
    }
    public static Object get(String path,Map<String,Object> data){
        String[] keys = path.split("\\.");
        Map<String,Object> temp;
        temp = getHashMapData(keys,data);
        return temp.get(keys[keys.length - 1]);
    }
    public static Map<String,Object> getHashMapData(String[] keys, Map<String,Object> data){
        Map<String,Object> currentMap = data;
        for(int i = 0 ; i < keys.length -1 ; i++){
            String key = keys[i];
            if(currentMap.containsKey(key)) {
                Object value = currentMap.get(key);
                if(value instanceof Map){
                    currentMap = (Map<String,Object>)value;
                } else {
                    return  currentMap;
                }
            }else{
                return null;
            }
        }
        return currentMap;
    }

    @Override
    public void set(String path, Object val, Map<String, Object>  data){
        String[] keys= path.split("\\.");
        Map<String,Object> map = getHashMapData(keys,data);
        if (map != null) {
            map.put(keys[keys.length - 1], val);
        }
    }

    @Override
    public void set(String path,Object val){
        set(path,val,configData);
    }

    @Override
    public void save(File file,Map<String,Object> data){
//        LinkedHashMap<Integer,String> comments = getComments(file);
        try(FileWriter writer = new FileWriter(file)){
            yaml.dump(data,writer);
        }catch(Exception e){
            e.printStackTrace();
        }
        WaterPlugin.getLogger().info(Basics.parseColor("§aSuccessfully save config"));
    }

    public static DumperOptions getDumperOptions(){
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return options;
    }

    public void loadDefaultSource(Boolean CreateFiles){
        if (CreateFiles) { createConfigFiles(); };
        configData= loadSource("config.yml");
        configData.putAll(loadSource("message.yml"));
        loadPluginMessages((String)configData.get("locale"));
    }
    @Override
    public void saveConfig(){
        save(new File(getPluginFilePath("config.yml")),configData);
    }
}
