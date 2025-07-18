package me.waterwood.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.velochatx.events.PlayerEvents;
import me.waterwood.velochatx.manager.ChannelManager;
import org.waterwood.enums.COLOR;
import org.waterwood.plugin.velocity.VelocityPlugin;
import me.waterwood.velochatx.commands.ControlCommands;
import me.waterwood.velochatx.commands.MentionCommand;
import me.waterwood.velochatx.commands.MsgCommand;
import me.waterwood.velochatx.manager.BasicMethods;


@Plugin(
        id = "velochatx",
        name = "VeloChatX",
        authors = "Waterwood",
        version = "2.0.3")
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
        logMsg(getPluginMessage("init-process-message"));
        if(! getConfigs().get("enable",true)) {
            logMsg(getPluginMessage("plugin-disable-message"),COLOR.RED);
            return;
        }
        init();
        logMsg(String.format(getPluginMessage("successfully-enable-message"),System.currentTimeMillis() - start));
        Metrics metrics = metricsFactory.make(this, 23273);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {

    }
    
    public void init(){
        BasicMethods.load();
        server.getEventManager().register(this, new PlayerEvents());
        registerCommands();
        checkUpdate("Danburen","VeloChatX","2.0.1", getConfigs(), ChannelManager.getBroadcastConfigs());
    }

    public void registerCommands(){
        new MentionCommand().register(this);
        new MsgCommand().register(this);
        new ControlCommands().register(this);
    }
}
