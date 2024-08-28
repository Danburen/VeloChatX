package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.waterwood.VelocityPlugin;
import me.waterwood.common.PluginBase;
import me.waterwood.common.Colors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.List;

public abstract class VelocitySimpleCommand extends PluginBase {
    public void register(VelocityPlugin plugin, SimpleCommand command, String PRIMARY_ALIAS, String[] ALIASES, boolean sharp){
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

    public void illegalArgsMsg(CommandSource source,String command){
        source.sendMessage(Component.text(getMessage("incorrect-command-arguments-message")
                .formatted(getMessage(command + "-command-format-message")), NamedTextColor.RED));
    }

    public void failFindPlayerMsg(CommandSource source,String playerName){
        if(source instanceof Player){
            source.sendMessage(Component.text(getMessage("fail-find-player-message").formatted(playerName)));
        }else{
            source.sendMessage(Component.text(Colors.parseColor(getMessage("fail-find-player-message").formatted(playerName))));
        }
    }

    public boolean checkNoSelf(CommandSource source,Player targetPlayer){
        if (source.equals(targetPlayer)) {
            source.sendMessage(Component.text(getMessage("no-self-action-message")));
                return true;
        }
        return false;
    }
    public void sendRawMessage(CommandSource source, String msg){
        source.sendMessage(Component.text(msg));
    }
    public abstract void register(VelocityPlugin plugin);
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

