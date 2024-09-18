package org.waterwood.io.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class Updater extends WebIO {

    /**
     * Checking for update , config  will automatically load.
     * API GET LIKE: https://api.github.com/repos/[NAME]/[REPOSITORIES]/releases/latest
     * @param gitUserName GitHubUsername
     * @param repositories your project's repositories
     * @param currentVersion current version
     * @return CompletableFuture contains update info
     */
    public static CompletableFuture<Map<String, Object>> CheckForUpdata(String gitUserName, String repositories, Double currentVersion){
        return CompletableFuture.supplyAsync(() ->{
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
                    return Map.of("latestVersion", latestVersion,"hasNewVersion", false,
                            "status", 1);
                } else {
                    return Map.of("downloadLink", downloadLink,
                            "latestVersion", latestVersion, "hasNewVersion", true,
                            "status", 1);
                }
            }catch (Exception e){
                return null;
            }
        });
    }
    /**
     * async download from url
     * @param fileUrl location of the url
     * @param savedPath where file would be downloaded at.
     * @return CompletableFuture
     */
    public static CompletableFuture<Boolean> downloadFile(String fileUrl, String savedPath){
        return CompletableFuture.supplyAsync(() -> {
            try {
                download(fileUrl,savedPath);
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }

    public static double parseVersion(String dotStr){
        int dotInd = dotStr.indexOf(".");
        String out;
        if(dotInd != -1){
            out = dotStr.substring(0,dotInd + 1) + dotStr.substring(dotInd + 1).replaceAll("\\.","");
            double num = Double.parseDouble(out);
            return num;
        }else{
            return 0.0f;
        }
    }
}

