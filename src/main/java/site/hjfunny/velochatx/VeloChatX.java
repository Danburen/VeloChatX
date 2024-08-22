package site.hjfunny.velochatx;

import me.waterwood.common.Color;
import me.waterwood.config.FileConfiguration;
import me.waterwood.plugin.WaterPlugin;
import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(
        id = "velochatx",
        name = "VeloChatX",
        version = "1.0",
        authors = "Waterwood")
public class VeloChatX extends WaterPlugin {
    private String  PluginPrefix = "[§2VeloChatX§r]";
    private final Logger logger;
    private final ProxyServer server;
    private static VeloChatX Instance;

    private FileConfiguration config = null;
    @Inject
    public VeloChatX(ProxyServer server , Logger logger){
        super(logger);
        this.logger = logger;
        this.server = server;
    }


    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        Instance = this;
        server.getEventManager().register(this, new ChatEvent());
        ChatProcesser.init();
        if(! ChatProcesser.hasLuckPerm()){
            logger.warn("Cannot connect to LuckPerms,perfix && suffix is unavailable.");
        }
        try{
            config = getConfig().loadConfig();
            logger.info(getConfig().getDouble("ConfigVersion").toString());
            config.set("ConfigVersion",99.1);
            config.saveConfig();
            logger.info(getConfig().getDouble("ConfigVersion").toString());
        }catch(Exception e){
            logger.warn("Can't load the config file make sure the file is existed and is not occupied");
            return;
        }
        logger.info(Color.paint("blue"," _____   _____   _       _____   _____   _   _       ___   _____  __    __ "));
        logger.info(Color.paint("blue","|  _  \\ | ____| | |     /  _  \\ /  ___| | | | |     /   | |_   _| \\ \\  / / "));
        logger.info(Color.paint("blue","| | | | | |__   | |     | | | | | |     | |_| |    / /| |   | |    \\ \\/ / "));
        logger.info(Color.paint("blue","| | | | |  __|  | |     | | | | | |     |  _  |   / / | |   | |     }  {   "));
        logger.info(Color.paint("blue","| |_| | | |___  | |___  | |_| | | |___  | | | |  / /  | |   | |    / /\\ \\  "));
        logger.info(Color.paint("blue","|_____/ |_____| |_____| \\_____/ \\_____| |_| |_| /_/   |_|   |_|   /_/  \\_\\ "));
        logger.info(Color.paint("green",String.format("VelochatX V%s  Author:%s",getPluginData().get("version"),getPluginData().get("author"))) );
    }
    public ProxyServer getProxyServer(){
        return server;
    }
    public static VeloChatX getInstance(){
        return Instance;
    }
}
