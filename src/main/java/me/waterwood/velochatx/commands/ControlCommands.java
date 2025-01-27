package me.waterwood.velochatx.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.velochatx.manager.BroadCastManager;
import me.waterwood.velochatx.manager.PlayerManager;
import me.waterwood.velochatx.utils.PlayerAttribution;
import me.waterwood.velochatx.VeloChatX;
import me.waterwood.velochatx.events.PlayerEvents;
import me.waterwood.velochatx.methods.Methods;
import me.waterwood.velochatx.utils.Channel;
import me.waterwood.velochatx.utils.SubServer;
import org.waterwood.utils.Colors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.waterwood.plugin.velocity.VelocityPlugin;
import me.waterwood.velochatx.methods.MessageMethods;

import java.io.IOException;
import java.util.*;

public class ControlCommands extends VelocitySimpleCommand implements SimpleCommand {
    private static final List<String> playerActions = List.of("reject","remove","ignore");
    private static final String PRIMARY_ALIAS = "velochatx";
    private static final String[] ALIASES = {"vcx","chatx","vc"};

    private static final List<String> defaultPlayerCommands = List.of("help", "on", "off", "ignore", "reject", "remove","list");
    private static final List<String> adminCommands = List.of("show-channel-info","help", "on", "off", "ignore", "reject", "remove","list");
    public static final ArrayList<String> configFiles = new ArrayList<>(Arrays.asList("config","message"));
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> subCmds = this.suggest(invocation);
        if(args.length > 2){
            illegalArgsMsg(source,"control");
            return;
        }
        if(args.length == 0){
            source.sendMessage(Component.text(Colors.parseColor(getPluginInfo())));
            return;
        }
        if(args[0].equalsIgnoreCase("reload")) { //reload command
            if (source.hasPermission("velochatx.admin")) {
                if (args.length == 1) {
                    VeloChatX.getInstance().reloadConfig();
                    Methods.load();
                    source.sendMessage(Component.text(getPluginMessage("config-reload-completed-message"), NamedTextColor.GREEN));
                    return;
                }
                if (configFiles.contains(args[1])) {
                    try {
                        VeloChatX.getInstance().reloadConfig(args[1]);
                        source.sendMessage(Component.text(getPluginMessage("config-file-reload-message")
                                .formatted(args[1].concat(".yml")), NamedTextColor.GREEN));
                    }catch (IOException e){
                        source.sendMessage(Component.text(getPluginMessage("config-file-reload-error-message").formatted(args[1].concat(".yml")),NamedTextColor.RED));
                    }
                } else {
                    source.sendMessage(Component.text(String.format(getPluginMessage("incorrect-config-file-message"), args[1], configFiles), NamedTextColor.RED));
                }
            } else {
                source.sendMessage(Component.text(getPluginMessage("no-permission"), NamedTextColor.RED));
            }
            return;
        }
        // global command
        if(source.hasPermission("velochatx.admin") || source instanceof ConsoleCommandSource){
            if(args[0].equalsIgnoreCase("show-channel-info")){
                for(Channel channel: BroadCastManager.getChannels().values()){
                    source.sendMessage(Component.text(channel.getChannelName() + " | " + channel.getChannelName() + " : ", NamedTextColor.GRAY));
                    for(SubServer server : channel.getServers()){
                        source.sendMessage(Component.text("  " + server.getServerDisplayName(), NamedTextColor.GRAY)
                                .append(Component.text(" : ",NamedTextColor.GRAY))
                                .append(Component.text(server.getPlayers().size() + " ONLINE",NamedTextColor.GOLD)));
                    }
                }
            }
        }

        // local command
        if(source instanceof Player sourcePlayer) {
            String language = sourcePlayer.getEffectiveLocale().getLanguage();
                if (args[0].equalsIgnoreCase("help")) {
                    for (String cmd : subCmds) {
                        source.sendMessage(Component.text(getMessage(cmd + "-command-format-message", language)));
                    }
                } else {
                    PlayerAttribution attrs = PlayerEvents.getPlayerAttrs().get(sourcePlayer.getUniqueId());
                    List<String> players = VelocityPlugin.getAllPlayerName();
                    if (args[0].equalsIgnoreCase("on")) { //velochatx on command
                        attrs.setAccess(true);
                        source.sendMessage(Component.text(getMessage("enable-vc-chat-message", language), NamedTextColor.GREEN));
                    } else if (args[0].equalsIgnoreCase("off")) { //velochatx off command
                        attrs.setAccess(false);
                        source.sendMessage(Component.text(getMessage("disable-vc-chat-message", language), NamedTextColor.DARK_RED));
                    } else if(args[0].equalsIgnoreCase("list")) { //velochatx list command
                        if(!attrs.getIgnorePlayers().isEmpty()){
                            source.sendMessage(Component.text(getMessage("ignore-list-show-message", language), NamedTextColor.GREEN));
                            attrs.getIgnorePlayers().forEach(
                                    playersUUID ->{
                                        String name = VelocityPlugin.getPlayerName(playersUUID);
                                        source.sendMessage(Component.text(getMessage(
                                                "list-show-item-message"
                                        ).replace("{name}",name)));
                                    }
                            );
                            return;
                        }
                        if(!attrs.getIgnorePlayers().isEmpty()){
                            source.sendMessage(Component.text(getMessage("reject-list-show-message", language), NamedTextColor.GREEN));
                            attrs.getRejectPlayers().forEach(
                                    playersUUID ->{
                                        String name = VelocityPlugin.getPlayerName(playersUUID);
                                        source.sendMessage(Component.text(getMessage(
                                                "list-show-item-message"
                                        ).replace("{name}",name)));
                                    }
                            );
                            return;
                        }
                        source.sendMessage(Component.text(getMessage("empty-list-show-message", language)));
                    }else { //velochatx ignore command
                        String command = args[0].toLowerCase();
                        if (args.length != 2) {
                            illegalArgsMsg(source, "ignore");
                            return;
                        }
                        if (!(players.contains(args[1]))) {
                            sendWarnMessage(source, getMessage("fail-find-player-message", language).formatted(args[1]));
                            return;
                        }
                        if (sourcePlayer.getUsername().equals(args[1])) {
                            sendWarnMessage(source, getMessage("no-self-action-message", language));
                            return;
                        }
                        Player targetPlayer = VelocityPlugin.getProxyServer().getPlayer(args[1]).get();
                        UUID targetUUID = targetPlayer.getUniqueId();
                        switch (command) {
                            case "ignore" -> {       // velochatX ignore command
                                attrs.addIgnorePlayers(targetUUID);
                                sendRawMessage(source, MessageMethods.convertMessage("has-ignore-message", targetPlayer, source));
                            }
                            case "reject" -> {                               // velochatX reject command
                                attrs.addRejectPlayers(targetUUID);
                                sendRawMessage(source, MessageMethods.convertMessage("has-reject-message", targetPlayer, source));
                            }
                            case "remove" -> {
                                Set<UUID> mutedPlayers = new HashSet<>();
                                mutedPlayers.addAll(attrs.getRejectPlayers());
                                mutedPlayers.addAll(attrs.getIgnorePlayers());
                                if (mutedPlayers.contains(targetUUID)) {
                                    attrs.removeIJ(targetUUID);
                                    sendRawMessage(source, MessageMethods.convertMessage("normal-chat-message", targetPlayer, source));
                                    sendRawMessage(targetPlayer, MessageMethods.convertMessage("normal-chat-message", source, targetPlayer));
                                } else {
                                    sendRawMessage(source, MessageMethods.convertMessage("no-in-list-message", targetPlayer, source));
                                }
                            }
                            default -> UnKnowMessage(source);
                        }
                    }
                }
        }else{
            if (args[0].equalsIgnoreCase("help")) { //help command
                source.sendMessage(Component.text(Colors.parseColor(getPluginInfo())));
                for (String cmd : subCmds) {
                    source.sendMessage(Component.text(Colors.parseColor(getMessage(cmd + "-command-format-message"))));
                }
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        int argNum = invocation.arguments().length;
        String[] args = invocation.arguments();
        if(argNum == 0) {
            if (invocation.source() instanceof ConsoleCommandSource) {
                return List.of("help", "reload","show-channel-info");
            } else {
                if(invocation.source().hasPermission("velochatx.admin")){
                    return adminCommands;
                }
                return defaultPlayerCommands;
            }
        }else if(argNum == 1) {
            if(playerActions.contains(args[0])) {
                return PlayerManager.getAllPlayer(invocation.source());
            }
        }
        return List.of();
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
