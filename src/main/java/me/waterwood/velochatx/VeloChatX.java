package me.waterwood.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.velochatx.events.PlayerEvents;
import me.waterwood.velochatx.manager.BroadCastManager;
import org.waterwood.io.web.ChangelogGetter;
import org.waterwood.plugin.velocity.VelocityPlugin;
import me.waterwood.velochatx.commands.ControlCommands;
import me.waterwood.velochatx.commands.MentionCommand;
import me.waterwood.velochatx.commands.MsgCommand;
import me.waterwood.velochatx.manager.BasicMethods;
import org.waterwood.utils.Colors;

import java.util.Locale;


@Plugin(
        id = "velochatx",
        name = "VeloChatX",
        authors = "Waterwood",
        version = "2.0.0")
public class VeloChatX extends VelocityPlugin {
    private final ProxyServer server;
    private static VeloChatX Instance;
    private final Metrics.Factory metricsFactory;
    @Inject
    public VeloChatX(ProxyServer server ,Metrics.Factory metricsFactory){
        super(server);
        this.server = server;
        this.metricsFactory = metricsFactory;
    }


    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        showPluginTitle("VeloChatX");
        long start = System.currentTimeMillis();
        Instance = this;
        this.loadConfig();
        init();
        checkUpdate("Danburen","VeloChatX","2.0.0",
                getConfigs(), BroadCastManager.getBroadcastConfigs());
        logMsg(String.format(getPluginMessage("successfully-enable-message"),System.currentTimeMillis() - start));
        Metrics metrics = metricsFactory.make(this, 23273);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {

    }
    
    public void init(){
        logMsg(getPluginMessage("init-process-message"));
        server.getEventManager().register(this, new PlayerEvents());
        BasicMethods.load();
        registerCommands();
    }

    public void registerCommands(){
        new MentionCommand().register(this);
        new MsgCommand().register(this);
        new ControlCommands().register(this);
    }

    public static VeloChatX getInstance(){
        return Instance;
    }
}
