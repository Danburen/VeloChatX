package org.waterwood.io;

import java.io.*;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public abstract class MemoryProcess {

    /**
     * split a parent file from jar to File system,split like [xxxx][FileName]
     * @param splitStr split str "xxx" + file name
     * @param targetFilesPath where subFile output
     * @param source where parent file is in jar package(will add"/")
     */
    public void extractResource(String splitStr, String targetFilesPath, String source){
        File parentPath = new File(targetFilesPath);
        if(! parentPath.exists()){
            parentPath.mkdir();
        }
        try(InputStream LangFIS = getClass().getResourceAsStream("/" + source)){
            BufferedReader reader = null;
            if (LangFIS != null) {
                reader = new BufferedReader(new InputStreamReader(LangFIS));
            }
            String line;
            while((line = reader.readLine())!= null){
                if(line.startsWith(splitStr)){
                    String subFilePath = targetFilesPath +"\\" + line.substring(line.indexOf(":") + 1);
                    File subFile = new File(subFilePath);
                    if(subFile.exists()){
                        while((line = reader.readLine()) != null){    //locate to next ####(####FILE_END)
                            if(line.startsWith(splitStr)){break;}
                        }
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
    /**
     * extract jar package resource to file system
     * @param targetFilePath target file path
     * @param source source file of the jar package
     */
    public void extractResource(String targetFilePath, String source){
        InputStream IS = getClass().getResourceAsStream("/" + source);
        if (IS == null) {
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
    public String getPluginFilePath(String  PluginName, String FileName){
        return getJarDir() + "\\" +PluginName + "\\" + FileName;
    }
    public boolean isResourceExist(String source){
        URL sourceURL = getClass().getClassLoader().getResource(source);
        return sourceURL != null;
    }
}
