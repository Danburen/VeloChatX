package me.waterwood.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.velochatx.events.PlayerEvents;
import org.waterwood.common.Colors;
import org.waterwood.plugin.velocity.VelocityPlugin;
import me.waterwood.velochatx.commands.ControlCommands;
import me.waterwood.velochatx.commands.MentionCommand;
import me.waterwood.velochatx.commands.MsgCommand;
import me.waterwood.velochatx.events.PlayerTabListEvents;
import me.waterwood.velochatx.methods.Methods;


@Plugin(
        id = "velochatx",
        name = "VeloChatX",
        authors = "Waterwood")
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
        checkUpdate("Danburen","VeloChatX","1.3.0");
        getLogger().info(Colors.parseColor(getPluginMessage("config-files-load-message")));
        init();
        getLogger().info(Colors.parseColor(String.format(getPluginMessage("successfully-enable-message"),System.currentTimeMillis() - start)));
        // metrics
        int pluginId = 23273;
        Metrics metrics = metricsFactory.make(this, pluginId);
    }
    @Subscribe
    public void init(){
        getLogger().info(Colors.parseColor(getPluginMessage("init-process-message")));
        server.getEventManager().register(this, new PlayerEvents());
        server.getEventManager().register(this,new PlayerTabListEvents(getProxyServer(),this));
        Methods.load(getProxyServer());
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
