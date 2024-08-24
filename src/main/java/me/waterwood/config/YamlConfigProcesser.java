package me.waterwood.config;

import me.waterwood.common.Basics;
import me.waterwood.plugin.WaterPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.util.Map;

public class YamlConfigProcesser extends FileConfiguration{
    YamlConfigProcesser config = null;
    Yaml yaml = new Yaml(getDumperOptions());
    private static Map<String,Object> configData;
    public YamlConfigProcesser(){

    }
    @Override
    public FileConfiguration loadConfig(){
        if (config == null) localization();
        this.config = new YamlConfigProcesser();
        try {
            File configFile = new File(config.getPluginFilePath("config.yml"));
            InputStream configFIS = new FileInputStream(configFile);
            configData = yaml.load(configFIS);
            configFIS.close();
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

    /* I want to implement the function of saving comments for yaml file .
     this need to access the underlying of SnakeYaml so i quit....(further may try to achieve....)*/
    /*public static  LinkedHashMap<Integer,String> getComments(File file){
        Pattern COMMENT_PATTERN = Pattern.compile("(#.*|\\s*)$");
        LinkedHashMap<Integer,String> comments = new LinkedHashMap<>();
        try ( BufferedReader reader = new BufferedReader(new FileReader(file));){
            String line;
            int lineNumber = 0;
            while((line = reader.readLine())!= null) {
                lineNumber++;
                if (COMMENT_PATTERN.matcher(line).matches()) {
                    comments.put(lineNumber, line);
                }
            }
            return comments;
        }catch(IOException e){
            e.printStackTrace();
            return null;
        }
    }*/
    @Override
    public void saveConfig(){
        save(new File(getPluginFilePath("config.yml")),configData);
    }
}
