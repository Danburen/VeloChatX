package me.waterwood.config;

import me.waterwood.plugin.WaterPlugin;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.security.ProtectionDomain;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.Map;

public abstract class FileConfiguration implements FileConfigBase {
    public FileConfiguration(){
        extractResource(getPluginFilePath("config.yml"),"config.yml",true);
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
    public String getPluginFilePath(String FileName){
        return getJarDir() + "\\" +WaterPlugin.getPluginName() + "\\" + FileName;
    }

    /**
     * extract jar package resource to file system
     * @param targetFilePath target file path
     * @param source source file of the jar package
     * @param createTip whether create a FileName.yml.txt
     */
    public void extractResource(String targetFilePath, String source, Boolean createTip){
        InputStream IS = YamlConfigProcesser.class.getResourceAsStream("/" + source);
        if (IS == null) {
            WaterPlugin.getLogger().error("Config file not founded");
            return;
        }
        File targetFile =new File(targetFilePath);
        if (targetFile.exists()){
            return;
        }
        File parentDir = targetFile.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        try(OutputStream OS = new FileOutputStream(targetFile)){
            copyFile(IS,OS);
            if (createTip) {
                IS = YamlConfigProcesser.class.getResourceAsStream("/" + source);
                copyFile(IS, new FileOutputStream(new File(targetFilePath + ".txt")));
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /**
     * useInputStream to copy file
     * automatically close file
     * @param IS inputStream
     * @param OS outputStream
     */
    public void copyFile(InputStream IS,OutputStream OS){
        try{
            byte[] buffer = new byte[1024];
            int length;
            while((length = IS.read(buffer)) > 0){
                OS.write(buffer,0,length);
            }
            IS.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /**
     * use {@link ProtectionDomain} to get the jar package
     * @return String
     */
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
