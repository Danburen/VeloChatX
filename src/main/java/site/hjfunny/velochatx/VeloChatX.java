package site.hjfunny.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.plugin.WaterPlugin;
import org.slf4j.Logger;

import static me.waterwood.common.basics.parseColor;

@Plugin(
        id = "velochatx",
        name = "VeloChatX",
        version = "1.0",
        authors = "Waterwood")
public class VeloChatX extends WaterPlugin {
    private final Logger logger;
    private final ProxyServer server;
    private static VeloChatX Instance;
    @Inject
    public VeloChatX(ProxyServer server , Logger logger){
        super(logger);
        this.logger = logger;
        this.server = server;
    }


    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info(parseColor("§b _____   _____   _       _____   _____   _   _       ___   _____  __    __ "));
        logger.info(parseColor("§b|  _  \\ | ____| | |     /  _  \\ /  ___| | | | |     /   | |_   _| \\ \\  / / "));
        logger.info(parseColor("§b| | | | | |__   | |     | | | | | |     | |_| |    / /| |   | |    \\ \\/ / "));
        logger.info(parseColor("§b| | | | |  __|  | |     | | | | | |     |  _  |   / / | |   | |     }  {   "));
        logger.info(parseColor("§b| |_| | | |___  | |___  | |_| | | |___  | | | |  / /  | |   | |    / /\\ \\  "));
        logger.info(parseColor("§b|_____/ |_____| |_____| \\_____/ \\_____| |_| |_| /_/   |_|   |_|   /_/  \\_\\ "));
        logger.info(parseColor(String.format("§aVelochatX V%s  Author:%s",getPluginData().get("version"),getPluginData().get("author"))) );
        Instance = this;
        server.getEventManager().register(this, new ChatEvent());
        ChatProcesser.load();
        if(! ChatProcesser.hasLuckPerm()){
            logger.warn("Cannot connect to LuckPerms,perfix && suffix is unavailable.");
        }
        try{
            config = getConfig().loadConfig();
        }catch(Exception e){
            logger.warn("Can't load the config file make sure the file is existed and is not occupied");
        }
    }
    public ProxyServer getProxyServer(){
        return server;
    }
    public static VeloChatX getInstance(){
        return Instance;
    }
}
