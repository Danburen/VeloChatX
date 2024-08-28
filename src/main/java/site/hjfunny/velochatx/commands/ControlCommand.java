package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.VelocityPlugin;
import me.waterwood.common.Colors;
import me.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import site.hjfunny.velochatx.methods.MsgMethods;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ControlCommand extends VelocitySimpleCommand implements SimpleCommand {
    private static final String PRIMARY_ALIAS = "velochatx";
    private static final String[] ALIASES = {"vcx","chatx"};
    public static final ArrayList<String> configFiles = new ArrayList<>(Arrays.asList("config","message"));
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> subCmds = this.suggest(invocation);
        if(args.length < 1 || args.length > 2){
            illegalArgsMsg(source,"control");
            return;
        }
        if(!(subCmds.contains(args[0]))){
            illegalArgsMsg(source,"control");
            return;
        }
        if(source instanceof Player) {
            Player sourcePlayer = (Player) source;
            List<String> players = VelocityPlugin.getAllPlayerName();
            String language = sourcePlayer.getEffectiveLocale().getLanguage();
            if (args[0].equalsIgnoreCase("help")) {
                source.sendMessage(Component.text( "§1VeloChatX§r §bv%s§r §2By:%s§r"
                        .formatted(getMessage("version"), getMessage("author"))));
                for (String cmd : subCmds) {
                    source.sendMessage(Component.text(getMessage(cmd + "-command-format-message",language), NamedTextColor.AQUA));
                }
            } else {
                    source.sendMessage(Component.text(getMessage("only-in-game-message",language), NamedTextColor.RED));
            }
        }else{
            if (args[0].equalsIgnoreCase("help")) { //help command
                source.sendMessage(Component.text(Colors.parseColor("§1VeloChatX§r §bv%s§r §2By:%s§r"
                        .formatted(WaterPlugin.getPluginInfo("version"), WaterPlugin.getPluginInfo("author")))));
                for (String cmd : subCmds) {
                    source.sendMessage(Component.text(getMessage(cmd + "-command-format-message"), NamedTextColor.AQUA));
                }
            } else if(args[0].equalsIgnoreCase("reload")){ //reload command
                if(source.hasPermission("velochatx.admin")){
                    if(args.length == 1){
                        WaterPlugin.getConfig().reloadConfig();
                        source.sendMessage(Component.text(getMessage("config-reload-completed-message"),NamedTextColor.GREEN));
                        return;
                    }
                    if(configFiles.contains(args[1])) {
                        WaterPlugin.getConfig().reloadConfig(args[1] + ".yml");
                        source.sendMessage(Component.text(getMessage("config-file-reload-message")
                                .formatted(args[1].concat(".yml")),NamedTextColor.GREEN));
                    }else{
                        source.sendMessage(Component.text(String.format(getMessage("incorrect-config-file-message"),args[1],configFiles),NamedTextColor.RED));
                    }
                }else{
                    source.sendMessage(Component.text(getMessage("no-permission"),NamedTextColor.RED));
                }
            }else{
                source.sendMessage(Component.text(getMessage("unknown-command-message"),NamedTextColor.RED));
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if(invocation.source() instanceof ConsoleCommandSource){
            return List.of("help","reload");
        }else{
            return List.of("help","on","off","ignore");
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velochatx.player");
    }

    @Override
    public void register(VelocityPlugin plugin){
        this.register(plugin,this,PRIMARY_ALIAS,ALIASES,false);
    }
}
