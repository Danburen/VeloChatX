package me.waterwood.config;

import me.waterwood.plugin.WaterPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Locale;

public abstract class MemoryProcesser {
    public static final String getUserLanguage(){
        Locale userLocal = Locale.getDefault();
        return userLocal.getLanguage();
    }

    public abstract boolean isResourceExist(String source);
    /**
     * split a parent file from jar to File system,split like [xxxx][FileName]
     * @param splitStr split str "xxx" + file name
     * @param targetFilesPath where subFile output
     * @param source where parent file is in jar package(will add"/")
     */
    public void extractResource(String splitStr, @NotNull String targetFilesPath, @NotNull String source){
        File parentPath = new File(targetFilesPath);
        if(! parentPath.exists()){
            parentPath.mkdir();
        }
        try(InputStream LangFIS = getClass().getResourceAsStream("/" + source)){;
            BufferedReader reader = new BufferedReader(new InputStreamReader(LangFIS));
            String line;
            while((line = reader.readLine())!= null){
                if(line.startsWith(splitStr)){
                    String subFilePath = targetFilesPath +"\\" + line.substring(line.indexOf(":") + 1);
                    WaterPlugin.getLogger().info(subFilePath);
                    File subFile = new File(subFilePath);
                    if(subFile.exists()){
                        continue;
                    }
                    try(BufferedWriter writer = new BufferedWriter(new FileWriter(subFile))){
                        String contentLine;
                        while((contentLine = reader.readLine() )!= null){
                            if (contentLine.startsWith(splitStr)){
                                break;
                            }
                            writer.write(contentLine + "\n");
                        }
                    }
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }
    public abstract String getPluginFilePath(String FileName);
    public abstract String getPluginFilePath();
    /**
     * Extract jar package resource to file system,not generate tip.txt
     * @param targetFilePath target file path
     * @param source source file of the jar package
     */
    public void extractResource(String targetFilePath, String source ){
        extractResource(targetFilePath,source,false);
    }
    /**
     * extract jar package resource to file system
     * @param targetFilePath target file path
     * @param source source file of the jar package
     * @param createTip whether create a FileName.yml.txt
     */
    public void extractResource(String targetFilePath, String source, Boolean createTip){
        InputStream IS = getClass().getResourceAsStream("/" + source);
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
                IS = getClass().getResourceAsStream("/" + source);
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
    public abstract String getJarDir();
}
