package org.nitrowater.velochatx.commands;

import org.nitrowater.waterapi.domain.command.CommandManager;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.spi.CommandHandler;
import org.nitrowater.waterapi.domain.spi.CommandSource;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.velochatx.domain.shared.CommandHelper;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXCustomMessage;

import java.util.*;

/**
 * Main control command handler for VeloChatX.
 * <p>
 * Handles: /velochatx (aliases: /vcx, /chatx, /vc)
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class ControlCommandHandler implements CommandHandler {

    private final ServiceFactory serviceFactory;
    private final CommandHelper helper;

    private static final String ADMIN_PERMISSION = "velochatx.admin";
    private static final String PLAYER_PERMISSION = "velochatx.player";

    private static final List<String> BLOCK_ACTIONS = List.of("block", "unblock");
    private static final List<String> CONFIG_FILES = List.of("config", "message", "broadcast");
    private static final List<String> CONFIG_ACTIONS = List.of("reload", "update", "diff");

    /** A subcommand together with the requirements to see/use it. */
    private record SubCommand(String name, String permission, boolean playerOnly) {
    }

    /** Commands listed to a source holding {@link #ADMIN_PERMISSION}. */
    private static final List<SubCommand> ADMIN_COMMANDS = List.of(
            new SubCommand("help", null, false),
            new SubCommand("config", ADMIN_PERMISSION, false),
            new SubCommand("reload", ADMIN_PERMISSION, false),
            new SubCommand("show-channel-info", ADMIN_PERMISSION, false),
            new SubCommand("placeholder", ADMIN_PERMISSION, true),
            new SubCommand("on", PLAYER_PERMISSION, true),
            new SubCommand("off", PLAYER_PERMISSION, true),
            new SubCommand("block", PLAYER_PERMISSION, true),
            new SubCommand("unblock", PLAYER_PERMISSION, true),
            new SubCommand("list", PLAYER_PERMISSION, true)
    );

    /** Commands listed otherwise. {@code help} is always available to everyone. */
    private static final List<SubCommand> DEFAULT_COMMANDS = List.of(
            new SubCommand("help", null, false),
            new SubCommand("on", PLAYER_PERMISSION, true),
            new SubCommand("off", PLAYER_PERMISSION, true),
            new SubCommand("block", PLAYER_PERMISSION, true),
            new SubCommand("unblock", PLAYER_PERMISSION, true),
            new SubCommand("list", PLAYER_PERMISSION, true)
    );

    /** Player-only action subcommands. */
    private static final Set<String> PLAYER_SUBCOMMANDS =
            Set.of("on", "off", "block", "unblock", "list");

    public ControlCommandHandler(ServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
        this.helper = serviceFactory.getCommandHelper();
    }

    @Override
    public boolean execute(CommandSource source, String[] args) {
        if (args.length > 3) {
            helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            return false;
        }
        if (args.length == 0) {
            helper.sendInfo(source, pluginIdentity());
            return true;
        }

        String subCommand = args[0].toLowerCase();
        // Help is always reachable, for every player and the console.
        if (subCommand.equals("help")) {
            return handleHelp(source);
        }
        // Generic config subcommands (reload / update / diff) are provided by the kernel
        CommandManager commands = serviceFactory.getKernel().getCommandManager();
        if (commands != null && commands.executeGeneric(source, subCommand, args)) {
            return true;
        }
        // Admin commands
        if (source.hasPermission(ADMIN_PERMISSION)) {
            switch (subCommand) {
                case "show-channel-info" -> {
                    return handleShowChannelInfo(source);
                }
                case "placeholder" -> {
                    if (!source.isPlayer()) {
                        helper.sendError(source, VeloChatXCustomMessage.ONLY_IN_GAME);
                        return false;
                    }
                    return handlePlaceholder(source, args);
                }
                default -> {
                }
            }
        }
        // Player commands (game only). The registrar already requires `velochatx.player`.
        if (PLAYER_SUBCOMMANDS.contains(subCommand)) {
            if (!source.isPlayer()) {
                helper.sendError(source, VeloChatXCustomMessage.ONLY_IN_GAME);
                return false;
            }
            return handlePlayerCommand(source, subCommand, args);
        }

        helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
        return false;
    }

    private String pluginIdentity() {
        return serviceFactory.getKernel().getPluginContext().getPluginName() + " v" +
                serviceFactory.getKernel().getPluginContext().getMeta().getVersion();
    }

    private boolean handleHelp(CommandSource source) {
        helper.sendInfo(source, pluginIdentity());

        List<SubCommand> commands = source.hasPermission(ADMIN_PERMISSION) ? ADMIN_COMMANDS : DEFAULT_COMMANDS;
        Set<VeloChatXSystemMessage> printed = new LinkedHashSet<>();
        for (SubCommand command : commands) {
            if (!isAvailable(source, command)) {
                continue;
            }
            VeloChatXSystemMessage template = templateFor(command.name());
            // block/unblock share one format: print each format only once
            if (template != null && printed.add(template)) {
                helper.sendInfo(source, helper.getMessage(template, source));
            }
        }
        return true;
    }

    /** A subcommand is listed/available only when its permission is held and, when player-only, the source is a player. */
    private static boolean isAvailable(CommandSource source, SubCommand command) {
        if (command.playerOnly() && !source.isPlayer()) {
            return false;
        }
        return command.permission() == null || source.hasPermission(command.permission());
    }

    private static VeloChatXSystemMessage templateFor(String name) {
        return switch (name) {
            case "help" -> VeloChatXSystemMessage.HELP_COMMAND_FORMAT;
            case "show-channel-info" -> VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_FORMAT;
            case "placeholder" -> VeloChatXSystemMessage.PLACEHOLDER_COMMAND_FORMAT;
            case "config" -> VeloChatXSystemMessage.CONFIG_COMMAND_FORMAT;
            case "reload" -> VeloChatXSystemMessage.RELOAD_COMMAND_FORMAT;
            case "on" -> VeloChatXSystemMessage.ON_COMMAND_FORMAT;
            case "off" -> VeloChatXSystemMessage.OFF_COMMAND_FORMAT;
            case "block", "unblock" -> VeloChatXSystemMessage.CONTROL_COMMAND_FORMAT;
            case "list" -> VeloChatXSystemMessage.LIST_COMMAND_FORMAT;
            default -> null;
        };
    }

    private boolean handleShowChannelInfo(CommandSource source) {
        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_TITLE, source));

        serviceFactory.getChannelService().getAllChannels().forEach(channel -> {
            String displayName = channel.getDisplayName();
            helper.sendInfo(source, channel.getName() + " | " + displayName + " : " +
                    channel.getOnlinePlayerCount() + " Player");
        });
        return true;
    }

    private boolean handlePlaceholder(CommandSource source, String[] args) {
        if (args.length == 2 && source.isPlayer()) {
            helper.toUPlayer(source).ifPresent(player -> {
                String resolved = serviceFactory.getKernel().getPlaceholderService()
                        .resolve(args[1], PlaceholderContext.builder()
                                .player(player)
                                .build());
                helper.sendInfo(source, resolved);
            });
        }
        return true;
    }

    private boolean handlePlayerCommand(CommandSource source, String subCommand, String[] args) {
        Optional<UPlayer> optPlayer = helper.toUPlayer(source);
        if (optPlayer.isEmpty()) {
            return false;
        }

        UPlayer player = optPlayer.get();
        PlayerService playerService = serviceFactory.getPlayerService();

        switch (subCommand) {
            case "on" -> {
                playerService.setChatOffline(player.getUuid(), false);
                helper.sendSuccess(source, VeloChatXCustomMessage.ENABLE_VC_CHAT);
                return true;
            }
            case "off" -> {
                playerService.setChatOffline(player.getUuid(), true);
                helper.sendInfo(source, "§4" + helper.getMessage(VeloChatXCustomMessage.DISABLE_VC_CHAT, source));
                return true;
            }
            case "list" -> {
                return handleList(source, playerService.getOrCreateAttrs(player.getUuid()));
            }
            case "block", "unblock" -> {
                return handleBlockAction(source, player, playerService, subCommand, args);
            }
            default -> {
                helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
                return false;
            }
        }
    }

    private boolean handleList(CommandSource source, org.nitrowater.velochatx.domain.player.PlayerAttribution attrs) {
        if (attrs.getBlockedPlayers().isEmpty()) {
            helper.sendInfo(source, helper.getMessage(VeloChatXCustomMessage.EMPTY_LIST_SHOW, source));
            return true;
        }

        helper.sendSuccess(source, (VeloChatXCustomMessage.REJECT_LIST_SHOW));
        attrs.getBlockedPlayers().forEach(uuid -> {
            String name = serviceFactory.getWProxyServer().getPlayer(uuid)
                    .map(UPlayer::getLoginName)
                    .orElse("Unknown");
            helper.sendInfo(source, "§6*§r " + name);
        });

        return true;
    }

    private boolean handleBlockAction(CommandSource source, UPlayer player, PlayerService playerService,
                                       String action, String[] args) {
        if (!helper.checkArgs(source, args, 2, 2)) {
            return false;
        }

        String targetName = args[1];
        if (!helper.checkNotSelf(source, targetName)) {
            // checkNotSelf already sent the error message
            return false;
        }

        Optional<UPlayer> optTarget = helper.getPlayer(targetName);
        if (optTarget.isEmpty()) {
            helper.sendError(source, VeloChatXCustomMessage.FAIL_FIND_PLAYER);
            return false;
        }

        UPlayer target = optTarget.get();
        UUID playerUuid = player.getUuid();
        UUID targetUuid = target.getUuid();

        PlaceholderContext targetContext = PlaceholderContext.builder()
                .player(target)
                .build();

        if (action.equals("block")) {
            playerService.blockPlayer(playerUuid, targetUuid);
            helper.sendInfo(source, serviceFactory.getKernel().getPlaceholderService()
                    .resolve(helper.getMessage(VeloChatXCustomMessage.HAS_BLOCK, source), targetContext));
        } else {
            playerService.unblockPlayer(playerUuid, targetUuid);
            helper.sendInfo(source, serviceFactory.getKernel().getPlaceholderService()
                    .resolve(helper.getMessage(VeloChatXCustomMessage.HAS_UNBLOCK, source), targetContext));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSource source, String[] args) {
        if (args.length == 0 || args.length == 1) {
            return availableCommandNames(source);
        }

        String subCommand = args[0].toLowerCase();
        // /<cmd> config <reload|update|diff> [name|all]
        if (subCommand.equals("config") && source.hasPermission(ADMIN_PERMISSION)) {
            if (args.length == 2) {
                return CONFIG_ACTIONS;
            }
            if (args.length == 3) {
                return CONFIG_FILES;
            }
            return List.of();
        }
        if (args.length == 2 && BLOCK_ACTIONS.contains(subCommand)
                && source.isPlayer() && source.hasPermission(PLAYER_PERMISSION)) {
            return helper.getPlayerNamesExcluding(source);
        }

        return List.of();
    }

    /** Subcommand names visible to the source (permission + source type filtered). */
    private List<String> availableCommandNames(CommandSource source) {
        List<String> names = new ArrayList<>();
        List<SubCommand> commands = source.hasPermission(ADMIN_PERMISSION) ? ADMIN_COMMANDS : DEFAULT_COMMANDS;
        commands.stream()
                .filter(command -> isAvailable(source, command))
                .map(SubCommand::name)
                .forEach(names::add);
        // Kernel-provided generic subcommands (config / reload) require admin.
        CommandManager manager = serviceFactory.getKernel().getCommandManager();
        if (manager != null && source.hasPermission(ADMIN_PERMISSION)) {
            for (String generic : manager.getGenericNames()) {
                if (!names.contains(generic)) {
                    names.add(generic);
                }
            }
        }
        return names;
    }

    @Override
    public String getPermission() {
        return "velochatx.player";
    }
}
