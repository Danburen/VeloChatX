package me.waterwood.plugin;

import me.waterwood.config.FileConfiguration;

import org.slf4j.Logger;

public interface PluginBase {
    static FileConfiguration getConfig(){
        return null;
    };
    Logger logger = null;

    static Logger getLogger() {
        return null;
    }
}
