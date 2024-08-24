package site.hjfunny.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import me.waterwood.common.ABasics;
import me.waterwood.plugin.WaterPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReloadCommand extends ABasics implements SimpleCommand {
    public static final ArrayList<String> configFiles = new ArrayList<>(Arrays.asList("config","message","all"));
    @Override
    public void execute(final Invocation invocation){
        if (! hasPermission(invocation)) return;
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if(args.length > 2){
            source.sendMessage(Component.text(getMessage("incorrect-command-arguments-message")
                    .formatted("/velochatx reload <config file>" + configFiles),NamedTextColor.RED));
        }
        if(args[0].equalsIgnoreCase("reload")){
            if(args.length == 1){
                WaterPlugin.getConfig().reloadConfig();
                source.sendMessage(Component.text(getMessage("config-reload-completed-message"),NamedTextColor.GREEN));
            }else{
                if(configFiles.contains(args[1])) {
                    WaterPlugin.getConfig().reloadConfig(args[1] + ".yml");
                    source.sendMessage(Component.text(getMessage("config-file-reload-message")
                            .formatted(args[1].concat(".yml")),NamedTextColor.GREEN));
                }else{
                    source.sendMessage(Component.text(String.format(getMessage("incorrect-config-file-message"),args[1],configFiles),NamedTextColor.RED));
                }
            }
        }
    }

    @Override
    public boolean hasPermission(final Invocation invocation){
        return invocation.source().hasPermission("velochatx.reload");
    }

    @Override
    public List<String> suggest(final Invocation invocation){
        int argLength = invocation.arguments().length;
        if (argLength == 0) {
            return List.of("reload");
        }else if(argLength == 1){
            return configFiles;
        }else{
            return List.of();
        }
    }
}
