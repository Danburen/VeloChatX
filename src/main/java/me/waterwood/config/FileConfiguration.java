package me.waterwood.config;

import me.waterwood.plugin.WaterPlugin;

import java.io.*;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public abstract class FileConfiguration extends MemoryProcesser implements FileConfigBase {
    public FileConfiguration(){

    }
    public abstract Object get(String path);
    public abstract void set(String path, Object val, Map<String,Object> data);
    public abstract void set(String path, Object val);
    public abstract void save(File file,Map<String,Object> data);
    public abstract void saveConfig();
    public  FileConfiguration loadConfig(){
        return this;
    };
    public final Map<String,String> getMap(String path){
        return  getMap(path,String.class);
    }
    public final <T> Map<String, T> getMap(String path, Class<T> typeClass) {
        Object out = get(path);
        if (out instanceof Map) {
            Map<?, ?> rawMap = (Map<?, ?>) out;
            Map<String, T> typedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() instanceof String && typeClass.isInstance(entry.getValue())) {
                    typedMap.put((String) entry.getKey(), typeClass.cast(entry.getValue()));
                }
            }
            return typedMap;
        } else {
            return null;
        }
    }
    public final Integer getInteger(String path){
        return (Integer) get(path);
    }

    public final Double getDouble(String path){
        return (Double) get(path);
    }
    public final Boolean getBoolean(String path){
        return (Boolean) get(path);
    }

    public final ArrayList<?> getStringList(String path){
        return (ArrayList<?>) get(path);
    }

    public final String getString(String path){
        return (String) get(path);
    }

    public void localization(){
        WaterPlugin.getLogger().info("New");
        String sourceLangFilePath = "lang/" + getUserLanguage()+".yml" ;
        if(isResourceExist(sourceLangFilePath)){
            extractResource("##",getPluginFilePath(),sourceLangFilePath);
        }else{
            extraDefaultSource("config.yml","message.yml");
        }
    }
    public void extraDefaultSource(String... sources){
        WaterPlugin.getLogger().info("Can't find lang source ,use default lang en");
        for(String source : sources){
            if (isResourceExist(source)){
                extractResource(getPluginFilePath(source),source,false);
            }
        }
    }
    @Override
    public  String getJarDir(){
        try {
            ProtectionDomain domain = this.getClass().getProtectionDomain();
            CodeSource source = domain.getCodeSource();
            URL location = source.getLocation();
            File jarFile = new File(location.toURI());
            File jarDir = jarFile.getParentFile();
            return jarDir.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    @Override
    public  String getPluginFilePath(String FileName){
        return getJarDir() + "\\" +WaterPlugin.getPluginName() + "\\" + FileName;
    }
    @Override
    public  String getPluginFilePath(){
        return getJarDir() + "\\" +WaterPlugin.getPluginName();
    }
    @Override
    public boolean isResourceExist(String source){
        URL sourceURL = getClass().getClassLoader().getResource(source);
        return sourceURL != null;
    }
 }
