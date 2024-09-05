package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.VelocityPlugin;
import me.waterwood.common.Colors;
import me.waterwood.plugin.WaterPlugin;
import site.hjfunny.velochatx.events.PlayerEvents;
import site.hjfunny.velochatx.methods.Methods;
import site.hjfunny.velochatx.methods.MsgMethods;

import java.util.List;

public class MsgCommand extends VelocitySimpleCommand implements SimpleCommand {
    private final  static  String PRIMARY_ALIAS = "vcmsg";
    private final static String[] ALIASES = {"vctell"};
    @Override
    public void register(VelocityPlugin plugin){
            this.register(plugin,this,PRIMARY_ALIAS,ALIASES,false);

    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if(args.length <2 ){
            illegalArgsMsg(source,"msg");
            return;
        }
        List<String> players = VelocityPlugin.getAllPlayerName();
        if(! players.contains(args[0])){
            failFindPlayerMsg(source,args[0]);
        }else{
            Player targetPlayer = VelocityPlugin.getProxyServer().getPlayer(args[0]).get();
            if(checkNoSelf(source,targetPlayer)) return;
            StringBuilder message = new StringBuilder(args[1]);
            for(int i = 2 ; i < args.length ; i ++){
                message.append(" ").append(args[i]);
            }
            if(source instanceof Player sourcePlayer){
                if(PlayerEvents.getPlayerAttrs().get(targetPlayer.getUsername()).getRejectPlayers().contains(sourcePlayer.getUsername())){
                    sendRawMessage(source,MsgMethods.convertMessage("msg-reject-message", targetPlayer, source));
                    return;
                }
                if(PlayerEvents.getPlayerAttrs().get(targetPlayer.getUsername()).getIgnorePlayers().contains(sourcePlayer.getUsername())){
                    return;
                }
                sendRawMessage(source, Methods.placeValue(getMessage("msg-to-message").replace("{Message}", message.toString()),targetPlayer));
                sendRawMessage(targetPlayer, Methods.placeValue(getMessage("msg-receive-message").replace("{Message}", message.toString()),sourcePlayer));
            }else{
                sendRawMessage(source, Colors.parseColor(
                        Methods.placeValue(getMessage("msg-to-message").replace("{Message}", message.toString()),targetPlayer)));
                sendRawMessage(targetPlayer, MsgMethods.convertServer(getMessage("msg-receive-message").replace("{Message}", message.toString()), source,targetPlayer));
            }

        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if(invocation.arguments().length ==0) {
            return MsgMethods.getAllPlayer(invocation.source());
        }else{
            return List.of();
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velochatx.player");
    }
}
