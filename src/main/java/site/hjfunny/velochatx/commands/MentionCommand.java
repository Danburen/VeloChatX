package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.VelocityPlugin;
import org.waterwood.io.FileConfiguration;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.sound.SoundStop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import site.hjfunny.velochatx.events.PlayerEvents;
import site.hjfunny.velochatx.methods.MsgMethods;

import java.time.Duration;
import java.util.List;
import java.util.OptionalLong;

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
                if(PlayerEvents.getPlayerAttrs().get(targetPlayer.getUsername()).getRejectPlayers().contains(sourcePlayer.getUsername())){
                    sendRawMessage(source,MsgMethods.convertMessage("msg-reject-message", targetPlayer, source));
                    return;
                }
                if(PlayerEvents.getPlayerAttrs().get(targetPlayer.getUsername()).getIgnorePlayers().contains(sourcePlayer.getUsername())){
                    return;
                }
            }
            if(checkNoSelf(source,targetPlayer)) return;
            FileConfiguration config = getConfig();
            sendRawMessage(source,MsgMethods.convertMessage("mention-to-message", targetPlayer, source));
            sendRawMessage(targetPlayer,MsgMethods.convertMessage("mention-receive-message", source, targetPlayer));
            if(config.getBoolean("mention-show-title.enable")) {
                String mainTitle = MsgMethods.convertMessage("mention-title-show-main",source,targetPlayer);
                String subTitle = MsgMethods.convertMessage("mention-title-show-sub",source,targetPlayer);
                int[] time = {config.getInteger("mention-show-title.time.fade-in"),
                        config.getInteger("mention-show-title.time.stay"),
                        config.getInteger("mention-show-title.time.fade-out")};
                targetPlayer.showTitle(new titleShow(mainTitle,subTitle,time));
            }
            targetPlayer.playSound(new soundPlay(Key.key("minecraft:block.note_block.pling"), Sound.Source.BLOCK),Sound.Emitter.self());
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

class soundPlay implements Sound {
    private final Key key;
    private final Source source;
    soundPlay(Key name,Source source){
        this.key = name;
        this.source = source;
    }

    @Override
    public @NotNull Key name() {
        return key;
    }

    @Override
    public @NotNull Source source() {
        return source;
    }

    @Override
    public float volume() {
        return 1f;
    }

    @Override
    public float pitch() {
        return 1f;
    }

    @Override
    public @NotNull OptionalLong seed() {
        return null;
    }

    @Override
    public @NotNull SoundStop asStop() {
        return null;
    }
}