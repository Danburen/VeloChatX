package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.common.ABasics;
import me.waterwood.common.Basics;
import me.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import site.hjfunny.velochatx.Methods;
import site.hjfunny.velochatx.sounds;

import java.util.List;

public class MentionCommand extends ABasics implements SimpleCommand {

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> players = WaterPlugin.getAllPlayerName();
        if(args.length != 1){
            source.sendMessage(Component.text(getMessage("incorrect-command-arguments-message")
                    .formatted("/mention [PlayerName]]"), NamedTextColor.RED));
        }
        if(! players.contains(args[0])){
            source.sendMessage(Component.text(getMessage("fail-find-player-message")));
        }else{
            Player sourcePlayer = source instanceof Player ? (Player) source : null;
            source.sendMessage(Component.text(Basics.parseColor(Methods.placeValue(getMessage("mention-to-message"),sourcePlayer))));

            Player targetPlayer = getServer().getPlayer(args[0]).get();
            targetPlayer.sendMessage(Component.text(Basics.parseColor(Methods.placeValue(getMessage("mention-receive-message"),targetPlayer))));
            targetPlayer.playSound(new sounds());
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return WaterPlugin.getAllPlayerName();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velochatx.mention");
    }

}
