package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.common.ABasics;
import me.waterwood.plugin.WaterPlugin;

import java.util.Arrays;
import java.util.List;

public abstract class Command extends ABasics {
    public void register(WaterPlugin plugin, SimpleCommand command,String PRIMARY_ALIAS, String[] ALIASES, boolean sharp){
        ProxyServer proxy= plugin.getProxyServer();
        proxy.getCommandManager().register(PRIMARY_ALIAS,command,ALIASES);
        if(sharp){
            String SHARP_PRIMARY_ALIAS = '/' + PRIMARY_ALIAS;
            String[] SHARP_ALIASES = Arrays.stream(ALIASES).map(s -> "/" + s).toArray(String[]::new);
            proxy.getCommandManager().register(SHARP_PRIMARY_ALIAS,new ForwardingCommand(command){
                @Override
                public boolean hasPermission(final Invocation invocation){
                    return invocation.source() instanceof ConsoleCommandSource;
                }
            },SHARP_ALIASES);
        }
    }

    public abstract void register(WaterPlugin plugin);
}

class ForwardingCommand implements SimpleCommand{
    private final SimpleCommand fcmd;
    ForwardingCommand(SimpleCommand fcmd){
        this.fcmd = fcmd;
    }

    @Override
    public void execute(Invocation invocation) {
        this.fcmd.execute(invocation);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return this.fcmd.suggest(invocation);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return this.fcmd.hasPermission(invocation);
    }
}

