package me.waterwood.common;

import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.plugin.WaterPlugin;

public abstract class ABasics implements Basics {
    protected static String getMessage(String key){return WaterPlugin.getConfig().getString(key);}
    protected  static ProxyServer getServer(){return WaterPlugin.getProxyServer();}
}
