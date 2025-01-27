package me.waterwood.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.velochatx.events.PlayerEvents;
import me.waterwood.velochatx.manager.PlayerManager;
import org.waterwood.io.FileConfiguration;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.waterwood.plugin.velocity.VelocityPlugin;
import me.waterwood.velochatx.methods.MessageMethods;

import java.time.Duration;
import java.util.List;

public class  MentionCommand extends VelocitySimpleCommand implements SimpleCommand {
    private final  static  String PRIMARY_ALIAS = "mention";
    private final static String[] ALIASES = {"men","at"};
    public void register(VelocityPlugin plugin){
        this.register(plugin,this,PRIMARY_ALIAS,ALIASES,false);
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> players = VelocityPlugin.getAllPlayerName();
        if(args.length != 1){
            illegalArgsMsg(source,"mention");
            return;
        }
        if(! players.contains(args[0])){
            failFindPlayerMsg(source,args[0]);
        }else{
            Player targetPlayer = VelocityPlugin.getProxyServer().getPlayer(args[0]).get();
            if(source instanceof Player sourcePlayer){
                if(PlayerEvents.getPlayerAttrs().get(targetPlayer.getUniqueId()).getRejectPlayers().contains(sourcePlayer.getUniqueId())){
                    sendRawMessage(source, MessageMethods.convertMessage("msg-reject-message", targetPlayer, source));
                    return;
                }
                if(PlayerEvents.getPlayerAttrs().get(targetPlayer.getUniqueId()).getIgnorePlayers().contains(sourcePlayer.getUniqueId())){
                    return;
                }
            }
            if(checkNoSelf(source,targetPlayer)) return;
            FileConfiguration config = getConfigs();
            sendRawMessage(source, MessageMethods.convertMessage("mention-to-message", targetPlayer, source));
            sendRawMessage(targetPlayer, MessageMethods.convertMessage("mention-receive-message", source, targetPlayer));
            if(config.getBoolean("mention-show-title.enable")) {
                String mainTitle = MessageMethods.convertMessage("mention-title-show-main",source,targetPlayer);
                String subTitle = MessageMethods.convertMessage("mention-title-show-sub",source,targetPlayer);
                int[] time = {config.getInteger("mention-show-title.time.fade-in"),
                        config.getInteger("mention-show-title.time.stay"),
                        config.getInteger("mention-show-title.time.fade-out")};
                targetPlayer.showTitle(new titleShow(mainTitle,subTitle,time));
            }
            targetPlayer.playSound(Sound.sound(Key.key("minecraft:block.note_block.pling"), Sound.Source.BLOCK,1f,1f),Sound.Emitter.self());
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return PlayerManager.getAllPlayer(invocation.source());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velochatx.player");
    }

}
class titleShow implements Title {
    private static String mainTitleText;
    private static String subTitleText;
    private static int[] time;
    titleShow(String main,String sub,int[] time){
        mainTitleText = main;
        subTitleText = sub;
        titleShow.time = time;
    }
    @Override
    public @NotNull Component title() {
        return Component.text(mainTitleText);
    }

    @Override
    public @NotNull Component subtitle() {
        return Component.text(subTitleText);
    }

    @Override
    public @Nullable Times times() {
        return Times.times(Duration.ofSeconds(time[0]),Duration.ofSeconds(time[1]),Duration.ofSeconds(time[2]));
    }

    @Override
    public <T> @UnknownNullability T part(@NotNull TitlePart<T> part) {
        return null;
    }
}