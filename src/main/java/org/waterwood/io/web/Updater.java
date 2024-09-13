package org.waterwood.io.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public abstract class Updater {
    public static String sendGetRequest(String urlStr) {
        StringBuilder result = new StringBuilder();
        try{
            URL url = new URL(urlStr);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            int requestCode = connection.getResponseCode();
            if(requestCode == HttpURLConnection.HTTP_OK){
                InputStream IS = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(IS));
                String line;
                while((line = reader.readLine())!= null){
                    result.append(line);
                }
                IS.close();
                connection.disconnect();
            }
        }catch(Exception e) {
            return null;
        }
        return result.toString();
    }

    /**
     * Checking for update , config  will automatically load.
     * API GET LIKE: https://api.github.com/repos/[NAME]/[REPOSITORIES]/releases/latest
     * @param gitUserName your profile name
     * @param repositories your project's repositories
     */
    public static Map<String,Object> CheckForUpdata(String gitUserName, String repositories, Double currentVersion){
        String url = "https://api.github.com/repos/%s/%s/releases/latest".formatted(gitUserName,repositories);
            String latestJSON = sendGetRequest(url);
            try {
                JsonObject jsonObject = JsonParser.parseString(latestJSON).getAsJsonObject();
                String downloadLink = null;
                JsonArray assets = jsonObject.getAsJsonArray("assets");
                for (JsonElement asset : assets) {
                    downloadLink = asset.getAsJsonObject().get("browser_download_url").getAsString();
                    if (downloadLink != null) break;
                }
                String latestVersion = jsonObject.get("tag_name").getAsString();
                double latest = parseVersion(latestVersion);
                if (currentVersion >= latest) {
                    return Map.of("downloadLink", "",
                            "latestVersion", latestVersion, "hasNewVersion", false,
                            "status", 1);
                } else {
                    return Map.of("downloadLink", downloadLink,
                            "latestVersion", latestVersion, "hasNewVersion", true,
                            "status", 1);
                }
            }catch (Exception e){
                return null;
            }
    }

    public static double parseVersion(String dotStr){
        int dotInd = dotStr.indexOf(".");
        String out = dotStr;
        if(dotInd != -1){
            out = dotStr.substring(0,dotInd + 1) + dotStr.substring(dotInd + 1).replaceAll("\\.","");
            double num = Double.parseDouble(out);
            return num;
        }else{
            return 0.0f;
        }
    }
}

