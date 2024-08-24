package me.waterwood.plugin;

import me.waterwood.common.basics;
import me.waterwood.config.FileConfiguration;

import org.slf4j.Logger;


public interface PluginBase extends basics {
    static FileConfiguration getConfig(){
        return null;
    };
    Logger logger = null;

    static Logger getLogger() {
        return null;
    }
}
