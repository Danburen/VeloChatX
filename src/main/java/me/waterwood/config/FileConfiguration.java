package me.waterwood.config;

import me.waterwood.plugin.WaterPlugin;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.security.ProtectionDomain;
import java.security.CodeSource;
import java.util.Map;

public abstract class FileConfiguration implements FileConfigBase {
    public FileConfiguration(){
        extractConfigFromJar(getPluginFilePath("config.yml"));
    }
    public abstract Object get(String path);
    public abstract void set(String path, Object val, Map<String,Object> data);
    public abstract void set(String path, Object val);
    public abstract void save(File file,Map<String,Object> data);
    public abstract void saveConfig();
    public  FileConfiguration loadConfig(){
        return this;
    };
    public final Integer getInteger(String path){
        return (Integer) get(path);
    }

    public final Double getDouble(String path){
        return (Double) get(path);
    }
    public final Boolean getBoolean(String path){
        return (Boolean) get(path);
    }

    public final ArrayList<String> getStringList(String path){
        return (ArrayList<String>) get(path);
    }

    public final String getString(String path){
        return (String) get(path);
    }
    public String getPluginFilePath(String FileName){
        return getJarDir() + "\\" +WaterPlugin.getPluginName() + "\\" + FileName;
    }
    //resource process
    public abstract void extractConfigFromJar(String targetFilePath) ;

     public String getJarDir(){
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
 }
