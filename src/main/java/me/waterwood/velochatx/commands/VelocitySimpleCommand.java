package me.waterwood.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.waterwood.plugin.velocity.util.MethodBase;
import org.waterwood.utils.Colors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.waterwood.plugin.velocity.VelocityPlugin;

import java.util.Arrays;
import java.util.List;

public abstract class VelocitySimpleCommand extends MethodBase {
    public void register(VelocityPlugin plugin, SimpleCommand command, String PRIMARY_ALIAS, String[] ALIASES, boolean sharp){
        ProxyServer proxy= VelocityPlugin.getProxyServer();
        proxy.getCommandManager().register(PRIMARY_ALIAS, command, ALIASES);
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
        if(source instanceof Player sourcePlayer) {
            String lang = sourcePlayer.getEffectiveLocale().getLanguage();
            source.sendMessage(Component.text(getMessage("incorrect-command-arguments-message", lang)
                    .formatted(getMessage(command + "-command-format-message"),lang), NamedTextColor.RED));
        }else{
            source.sendMessage(Component.text(Colors.parseColor(getMessage("incorrect-command-arguments-message")
                    .formatted(getMessage(command + "-command-format-message"))), NamedTextColor.RED));
        }
    }

    public void failFindPlayerMsg(CommandSource source,String playerName){
        if(source instanceof Player sourcePlayer){
            source.sendMessage(Component.text(getMessage("fail-find-player-message",
                    sourcePlayer.getEffectiveLocale().getLanguage()).formatted(playerName)));
        }else{
            source.sendMessage(Component.text(Colors.parseColor(getMessage("fail-find-player-message").formatted(playerName))));
        }
    }

    public void checkNoSelf(CommandSource source,Player targetPlayer,commandHandler handler){
        if (source.equals(targetPlayer)) {
            source.sendMessage(Component.text(getMessage("no-self-action-message",targetPlayer.getEffectiveLocale().getLanguage())));
                return;
        }
        handler.execute();
    }
    public void sendRawMessage(CommandSource source, String msg){
        source.sendMessage(Component.text(msg));
    }

    public void sendWarnMessage(CommandSource source, String msg){
        source.sendMessage(Component.text(msg,NamedTextColor.RED));
    }

    public void isUnknownCommand(CommandSource source,List<String> commands,String command,commandHandler handler){
        if(!(commands.contains(command))){
            if(source instanceof Player sourcePlayer){
                source.sendMessage(Component.text(getMessage("unknown-command-message",
                        sourcePlayer.getEffectiveLocale().getLanguage()),NamedTextColor.RED));
            }else{
                sendWarnMessage(source,getMessage("unknown-command-message"));
            }
        }
        handler.execute();
    }

    public void UnKnowMessage(CommandSource source){
        if(source instanceof Player sourcePlayer){
            source.sendMessage(Component.text(getMessage("unknown-command-message",
                    sourcePlayer.getEffectiveLocale().getLanguage()),NamedTextColor.RED));
        }else{
            sendWarnMessage(source,"unknown-command-message");
        }
    }
    public abstract void register(VelocityPlugin plugin);

    public interface commandHandler{
        void execute();
    }
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

