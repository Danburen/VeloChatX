package me.waterwood.config;

import me.waterwood.common.Color;
import me.waterwood.plugin.WaterPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Node;

import java.io.*;
import java.util.Map;

public class YamlConfigProcesser extends FileConfiguration{
    YamlConfigProcesser config = null;
    private static Map<String,Object> configData;
    private static Map<String,Object> configComments;
    public YamlConfigProcesser(){

    }
    @Override
    public FileConfiguration loadConfig(){
        this.config = new YamlConfigProcesser();
        try {
            Yaml yaml = new Yaml();
            File configFile = new File(config.getPluginFilePath("config.yml"));
            InputStream configFIS = new FileInputStream(configFile);
            configData = yaml.load(configFIS);

        }catch(Exception e){
            e.printStackTrace();
        }
        return config;
    }
    @Override
    public final Object get(String path){
        return get(path,configData);
    }
    public static Object get(String path,Map<String,Object> data){
        String[] keys = path.split("\\.");
        Map<String,Object> temp;
        temp = getMap(keys,data);
        return temp.get(keys[keys.length - 1]);
    }

    public static Map<String,Object> getMap(String[] keys,Map<String,Object> data){
        Map<String,Object> currentMap = data;
        for(String key : keys){
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
        return null;
    }

    @Override
    public void set(String path, Object val, Map<String, Object>  data){
        String[] keys= path.split("\\.");
        Map<String,Object> map = getMap(keys,data);
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
        Yaml yaml = new Yaml(getDumperOptions());
        try(FileWriter fileWriter = new FileWriter(file)){
            yaml.dump(data,fileWriter);
        }catch(Exception e){
            e.printStackTrace();
        }
        WaterPlugin.getLogger().info(Color.paint("green","Successfully save config"));
    }
    @Override
    public void extractConfigFromJar(String targetFilePath){
        InputStream IS = YamlConfigProcesser.class.getResourceAsStream("/config.yml");
        if (IS == null) {
            WaterPlugin.getLogger().error("Config file not founded");
            return;
        }
        File targetFile =new File(targetFilePath);
        if (targetFile.exists()){
            return;
        }

        try{
            OutputStream OS = new FileOutputStream(targetFile);
            byte[] buffer = new byte[1024];
            int length;
            while((length = IS.read(buffer)) > 0){
                OS.write(buffer,0,length);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }
    public static DumperOptions getDumperOptions(){
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        return options;
    }
    @Override
    public void saveConfig(){
        save(new File(getPluginFilePath("config.yml")),configData);
    }
}
