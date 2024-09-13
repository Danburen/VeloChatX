package org.waterwood.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class FileConfiguration extends MemoryProcess implements FileConfigBase {
    public abstract Object get(String path);
    public abstract void set(String path, Object val, Map<String,Object> data);
    public abstract void set(String path, Object val);
    public abstract void save(File file,Map<String,Object> data) throws Exception;
    public abstract Map<String,Object> getFileMapData(String filePath) throws IOException;
    public abstract FileConfiguration reload(String resourcePath) throws IOException;

    public abstract void loadSource(String source, String extension) throws IOException;

    public abstract void loadSource(String sourcePath) throws IOException;
    public FileConfiguration loadConfig(){
        return this;
    };
    public FileConfiguration reload(){return this;}
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
    public boolean isResourceExist(String source){
        URL sourceURL = getClass().getClassLoader().getResource(source);
        return sourceURL != null;
    }

    public void createFile(String fileName,String pluginName) throws FileNotFoundException {
        String path = getPluginFilePath(pluginName,fileName + ".yml");
        String source = fileName + "/" + Locale.getDefault().getLanguage() + ".yml";
        if(isResourceExist(source)) {
            extractResource(path, source);
        }else{
            extractResource(path,fileName + "/en.yml");
            throw new FileNotFoundException();
        }
    }
 }
