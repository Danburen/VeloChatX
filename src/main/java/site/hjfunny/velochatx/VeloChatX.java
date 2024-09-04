package site.hjfunny.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.VelocityPlugin;
import me.waterwood.common.Colors;
import org.slf4j.Logger;
import site.hjfunny.velochatx.commands.ControlCommands;
import site.hjfunny.velochatx.commands.MentionCommand;
import site.hjfunny.velochatx.commands.MsgCommand;
import site.hjfunny.velochatx.events.CommandEvents;
import site.hjfunny.velochatx.events.PlayerEvents;
import site.hjfunny.velochatx.methods.Methods;
import me.waterwood.util.Metrics ;


@Plugin(
        id = "velochatx",
        name = "VeloChatX",
        version = "1.0",
        authors = "Waterwood")
public class VeloChatX extends VelocityPlugin {
    private final Logger logger;
    private final ProxyServer server;
    private static VeloChatX Instance;
    private final Metrics.Factory metricsFactory;
    @Inject
    public VeloChatX(ProxyServer server , Logger logger,Metrics.Factory metricsFactory){
        super(logger,server);
        this.logger = logger;
        this.server = server;
        this.metricsFactory = metricsFactory;
    }


    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info(Colors.parseColor("§1                                             "));
        logger.info(Colors.parseColor("§1\\  /  ___  |    __   __  |__   __  _|_  \\_'"));
        logger.info(Colors.parseColor("§1 \\/  (__/_ |_, (__) (___ |  ) (__(  |_, / \\"));
        logger.info(Colors.parseColor("§1                                             "));
        logger.info(Colors.parseColor("VeloChatX §bV%s §2Author:§rWaterwood".formatted(getPluginInfo("version"))));
        Long start = System.currentTimeMillis();
        Instance = this;
        config = getConfig();
        CheckForUpdata("Danburen","VeloChatX");
        logger.info(Colors.parseColor(config.getString("config-files-load-message")));
        init();
        logger.info(Colors.parseColor(String.format(config.getString("successfully-enable-message"),System.currentTimeMillis() - start)));
        // metrics
        int pluginId = 23273;
        Metrics metrics = metricsFactory.make(this, pluginId);
    }
    @Subscribe
    public void init(){
        logger.info(Colors.parseColor(config.getString("init-process-message")));
        server.getEventManager().register(this, new PlayerEvents());
        server.getEventManager().register(this, new CommandEvents());
        Methods.load();
        registerCommands();
    }

    @Subscribe
    public void registerCommands(){
        new MentionCommand().register(this);
        new MsgCommand().register(this);
        new ControlCommands().register(this);
    }

    public static VeloChatX getInstance(){
        return Instance;
    }
}
