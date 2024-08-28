package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.VelocityPlugin;
import me.waterwood.config.FileConfiguration;
import me.waterwood.plugin.WaterPlugin;
import site.hjfunny.velochatx.methods.MsgMethods;

import java.util.List;

public class MsgCommand extends VelocitySimpleCommand implements SimpleCommand {
    private final  static  String PRIMARY_ALIAS = "msg";
    private final static String[] ALIASES = {"vctell"};
    @Override
    public void register(VelocityPlugin plugin){
            this.register(plugin,this,PRIMARY_ALIAS,ALIASES,true);

    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if(args.length != 2){
            illegalArgsMsg(source,"msg");
            return;
        }
        List<String> players = VelocityPlugin.getAllPlayerName();
        if(! players.contains(args[0])){
            failFindPlayerMsg(source,args[0]);
        }else{
            Player targetPlayer = VelocityPlugin.getProxyServer().getPlayer(args[0]).get();
            if(checkNoSelf(source,targetPlayer)) return;;
            sendRawMessage(source,MsgMethods.convertMessage("msg-receive-message".formatted(args[1]), targetPlayer, source));
            sendRawMessage(targetPlayer,MsgMethods.convertMessage("msg-receive-message".formatted(args[1]), source, targetPlayer));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return MsgMethods.getAllPlayer(invocation.source());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velochatx.player");
    }
}
