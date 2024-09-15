package org.waterwood.io;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class FileConfigProcess extends FileConfiguration {
    private Map<String,Object> data;
    Yaml yaml = new Yaml(getDumperOptions());

    @Override
    public void set(String path, Object val, Map<String, Object>  data){
        String[] keys= path.split("\\.");
        Map<String,Object> map = (Map<String,Object>)getHashMapData(keys,data);
        if (map != null) {
            map.put(keys[keys.length - 1], val);
        }
    }

    @Override
    public void set(String path,Object val){
        set(path,val,data);
    }

    @Override
    public void save(File file,Map<String,Object> data) throws Exception {
        try(FileWriter writer = new FileWriter(file)){
            yaml.dump(data,writer);
        }catch(Exception e){
            throw new Exception();
        }
    }
    public FileConfigProcess loadFile(String filePath) throws IOException{
        data = getFileMapData(filePath);
        return this;
    }

    @Override
    public Map<String, Object> getFileMapData(String filePath) throws IOException {
        File file = new File(filePath);
        try(InputStream FIS = new FileInputStream(file)){
            return yaml.load(FIS);
        }catch (IOException e){
            throw new IOException(e);
        }
    }
    @Override
    public FileConfigProcess reload(String resourcePath) throws IOException {
        try {
            data.putAll(getFileMapData(resourcePath));
        }catch(IOException e){
            throw new IOException();
        }
        return this;
    }
    @Override
    public void loadSource(String source,String extension) throws IOException {
        data = getSourceMapData(source,extension);
    }

    public Map<String,Object> getSourceMapData(String sourcePathName,String extension) throws  IOException{
        String filePath = "/"  + sourcePathName + "." + extension;
        try(InputStream IS = getClass().getResourceAsStream(filePath)){
            if(extension.equals("yml")) {
                return  yaml.load(IS);
            }else if(extension.equals("properties")){
                Properties prop = new Properties();
                prop.load(new InputStreamReader(IS, StandardCharsets.UTF_8));
                return ((Map<Object,Object>) prop).entrySet().stream().collect(
                        Collectors.toMap(e -> e.getKey().toString(),Map.Entry::getValue));
            }else{
                return null;
            }
        }catch (IOException e){
            throw new IOException();
        }
    }
    @Override
    public void loadSource(String sourceFilePath) throws IOException {
        String extension = sourceFilePath.substring(sourceFilePath.lastIndexOf(".")+1);
        String filePath = sourceFilePath.substring(0,sourceFilePath.lastIndexOf("."));
        data = getSourceMapData(filePath,extension);
    }

    @Override
    public final Object get(String path){
        return get(path,data);
    }
    public static Object get(String path,Map<String,Object> data){
        String[] keys = path.split("\\.");
        return getHashMapData(keys,data);
    }

    public Map<String,Object> getData(){
        return data;
    }
    public static Object getHashMapData(String[] keys, Map<String,Object> data){
        Object currentData = data;
        for (String key : keys) {
            if (currentData instanceof Map) {
                Map<String, Object> currentMap = (Map<String, Object>) currentData;
                if (currentMap.containsKey(key)) {
                    currentData = currentMap.get(key);
                } else {
                    return null;
                }
            } else if (currentData instanceof List<?> currentList) {
                try {
                    int index = Integer.parseInt(key);
                    if (index >= 0 && index < currentList.size()) {
                        currentData = currentList.get(index);
                    } else {
                        return null;
                    }
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return currentData;
    }

    public static DumperOptions getDumperOptions(){
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return options;
    }
}
