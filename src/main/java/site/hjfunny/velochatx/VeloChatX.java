package site.hjfunny.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.plugin.WaterPlugin;
import org.slf4j.Logger;
import site.hjfunny.velochatx.commands.MentionCommand;
import site.hjfunny.velochatx.commands.ReloadCommand;
import site.hjfunny.velochatx.events.CommandEvents;
import site.hjfunny.velochatx.events.PlayerEvents;

import static me.waterwood.common.Basics.parseColor;

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
        super(logger,server);
        this.logger = logger;
        this.server = server;
    }


    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info(parseColor("                                               "));
        logger.info(parseColor("§1 __|  ___  |    __   __  |__   __  _|_  \\_'"));
        logger.info(parseColor("§1(__| (__/_ |_, (__) (___ |  ) (__(  |_, / \\"));
        logger.info(parseColor("                                               "));
        logger.info(parseColor("DeloChatX §bV%s §2Author:§rWaterwood".formatted(getPluginInfo("version"))));
        Long start = System.currentTimeMillis();
        Instance = this;
        config = getConfig();
        logger.info(parseColor(config.getString("config-files-load-message")));
        init();
        logger.info(parseColor(config.getString("successfully-enable-message").formatted(System.currentTimeMillis() - start)));
    }
    public void init(){
        logger.info(parseColor(config.getString("init-process-message")));
        server.getEventManager().register(this, new PlayerEvents());
        server.getEventManager().register(this, new CommandEvents());
        Methods.load();
        registerCommands();
    }

    public void registerCommands(){
        new ReloadCommand().register(this);
    }

    public static VeloChatX getInstance(){
        return Instance;
    }
}
