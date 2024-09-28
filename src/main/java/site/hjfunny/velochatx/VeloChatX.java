package site.hjfunny.velochatx;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;
import org.waterwood.common.Colors;
import org.waterwood.common.LineFontGenerator;
import org.waterwood.plugin.velocity.VelocityPlugin;
import site.hjfunny.velochatx.commands.ControlCommands;
import site.hjfunny.velochatx.commands.MentionCommand;
import site.hjfunny.velochatx.commands.MsgCommand;
import site.hjfunny.velochatx.events.PlayerEvents;
import site.hjfunny.velochatx.methods.Methods;


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
        checkUpdate("Danburen","VeloChatX");
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
