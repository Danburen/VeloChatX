package org.nitrowater.velochatx.commands;

import org.nitrowater.waterapi.domain.command.CommandManager;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.spi.CommandHandler;
import org.nitrowater.waterapi.domain.spi.CommandNode;
import org.nitrowater.waterapi.domain.spi.CommandSource;
import org.nitrowater.waterapi.plugin.PluginContext;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.velochatx.domain.shared.BindingDiagnostics;
import org.nitrowater.velochatx.domain.shared.CommandHelper;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.domain.shared.StatusSnapshot;
import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXCustomMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXPermissions;

import java.util.*;
import java.util.stream.Collectors;

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

    private static final String ADMIN_PERMISSION = VeloChatXPermissions.ADMIN;
    private static final String PLAYER_PERMISSION = VeloChatXPermissions.PLAYER;

    private static final List<String> BLOCK_ACTIONS = List.of("block", "unblock");
    private static final List<String> CONFIG_FILES = List.of("config", "message", "broadcast");
    private static final List<String> CONFIG_ACTIONS = List.of("reload", "update", "diff", "migration", "migrate");

    /** A subcommand together with the requirements to see/use it. */
    private record SubCommand(String name, String permission, boolean playerOnly) {
    }

    /** Commands listed to a source holding {@link #ADMIN_PERMISSION}. */
    private static final List<SubCommand> ADMIN_COMMANDS = List.of(
            new SubCommand("help", null, false),
            new SubCommand("config", ADMIN_PERMISSION, false),
            new SubCommand("reload", ADMIN_PERMISSION, false),
            new SubCommand("show-channel-info", ADMIN_PERMISSION, false),
            new SubCommand("status", ADMIN_PERMISSION, false),
            new SubCommand("placeholder", ADMIN_PERMISSION, true),
            new SubCommand("on", PLAYER_PERMISSION, true),
            new SubCommand("off", PLAYER_PERMISSION, true),
            new SubCommand("block", PLAYER_PERMISSION, true),
            new SubCommand("unblock", PLAYER_PERMISSION, true),
            new SubCommand("list", PLAYER_PERMISSION, true)
    );

    /**
     * Commands listed otherwise: every non-admin command. {@code help} is always available to
     * everyone. Derived from {@link #ADMIN_COMMANDS} so the two lists cannot drift apart.
     */
    private static final List<SubCommand> DEFAULT_COMMANDS = ADMIN_COMMANDS.stream()
            .filter(command -> !ADMIN_PERMISSION.equals(command.permission()))
            .toList();

    /** Player-only action subcommands, derived from the command table. */
    private static final Set<String> PLAYER_SUBCOMMANDS = ADMIN_COMMANDS.stream()
            .filter(SubCommand::playerOnly)
            .filter(command -> PLAYER_PERMISSION.equals(command.permission()))
            .map(SubCommand::name)
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Admin-permission subcommands, derived from the command table. Used to tell an unauthorized
     * source apart from a genuinely unknown subcommand, so a denial reports "no permission"
     * instead of the misleading "unknown command".
     */
    private static final Set<String> ADMIN_SUBCOMMANDS = ADMIN_COMMANDS.stream()
            .filter(command -> ADMIN_PERMISSION.equals(command.permission()))
            .map(SubCommand::name)
            .collect(Collectors.toUnmodifiableSet());

    public ControlCommandHandler(ServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
        this.helper = serviceFactory.getCommandHelper();
    }

    /**
     * Build the platform-agnostic command tree from the subcommand table.
     * <p>
     * Each subcommand becomes a literal child carrying its permission / player-only flag, so a
     * platform (Velocity renders Brigadier {@code requires(...)}) only exposes the subcommands a
     * source is allowed to use. Every leaf shares this handler's
     * {@link #execute(CommandSource, String[])}: argument zero is the subcommand, mirroring the
     * string-dispatch contract.
     * </p>
     *
     * @return the root node of the tree
     */
    public CommandNode buildTree() {
        CommandNode.Builder root = CommandNode.builder("velochatx")
                // Bare `/velochatx` still prints the identity / help line.
                .executor(this::execute);
        for (SubCommand command : ADMIN_COMMANDS) {
            root.child(CommandNode.builder(command.name())
                    .permission(command.permission())
                    .playerOnly(command.playerOnly())
                    .executor(this::execute)
                    .build());
        }
        return root.build();
    }


    private String pluginIdentity() {
        PluginContext context = serviceFactory.getKernel().getPluginContext();
        return context.getPluginName() + " v" + context.getMeta().getVersion();
    }

    private boolean handleHelp(CommandSource source) {
        helper.sendInfo(source, pluginIdentity());

        List<SubCommand> commands = source.hasPermission(ADMIN_PERMISSION) ? ADMIN_COMMANDS : DEFAULT_COMMANDS;
        Set<VeloChatXSystemMessage> printed = new LinkedHashSet<>();
        for (SubCommand command : commands) {
            if (!isAvailable(source, command)) {
                continue;
            }
            VeloChatXSystemMessage template = switch (command.name) {
                case "help" -> VeloChatXSystemMessage.HELP_COMMAND_FORMAT;
                case "show-channel-info" -> VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_FORMAT;
                case "status" -> VeloChatXSystemMessage.STATUS_COMMAND_FORMAT;
                case "placeholder" -> VeloChatXSystemMessage.PLACEHOLDER_COMMAND_FORMAT;
                case "config" -> VeloChatXSystemMessage.CONFIG_COMMAND_FORMAT;
                case "reload" -> VeloChatXSystemMessage.RELOAD_COMMAND_FORMAT;
                case "on" -> VeloChatXSystemMessage.ON_COMMAND_FORMAT;
                case "off" -> VeloChatXSystemMessage.OFF_COMMAND_FORMAT;
                case "block", "unblock" -> VeloChatXSystemMessage.CONTROL_COMMAND_FORMAT;
                case "list" -> VeloChatXSystemMessage.LIST_COMMAND_FORMAT;
                default -> null;
            };
            // block/unblock share one format: print each format only once
            if (template != null && printed.add(template)) {
                helper.sendInfo(source, helper.getMessage(template, source));
                // migration lives under `config` now: keep its own help line discoverable
                if (template == VeloChatXSystemMessage.CONFIG_COMMAND_FORMAT) {
                    helper.sendInfo(source,
                            helper.getMessage(VeloChatXSystemMessage.MIGRATE_COMMAND_FORMAT, source));
                }
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

    private boolean handleShowChannelInfo(CommandSource source) {
        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_TITLE, source));

        serviceFactory.getChannelService().getAllChannels().forEach(channel ->
                helper.sendInfo(source, helper.getMessage(
                        VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_ENTRY, source,
                        channel.getName(), channel.getDisplayName(), channel.getOnlinePlayerCount())));
        return true;
    }

    /**
     * Report a one-shot runtime status summary: how many servers are recognized (and how
     * many of them are online), how many channels are loaded and how many broadcast messages
     * exist. The numbers come from the same {@link StatusSnapshot} the debug-level startup
     * self-check uses.
     */
    private boolean handleStatus(CommandSource source) {
        StatusSnapshot snapshot = StatusSnapshot.collect(
                serviceFactory.getServerService(),
                serviceFactory.getChannelService(),
                serviceFactory.getBroadcastService());

        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.STATUS_COMMAND_TITLE, source));
        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.STATUS_SERVER_LINE, source,
                snapshot.getServerBound(), snapshot.getServerOnline(), snapshot.getServerOffline()));
        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.STATUS_CHANNEL_LINE, source,
                snapshot.getChannelCount()));
        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.STATUS_BROADCAST_LINE, source,
                snapshot.getBroadcastMessageTotal(),
                snapshot.getBroadcastGlobalMessages(),
                snapshot.getBroadcastLocaleMessages()));
        // Binding state per server: BOUND (declared + registered), UNBOUND (registered only),
        // PHANTOM (declared only), rendered in the source's locale.
        Map<String, String> states = snapshot.getBindingStates();
        helper.sendInfo(source, helper.getMessage(
                VeloChatXSystemMessage.STATUS_BINDING_TITLE, source, states.size()));
        states.forEach((name, state) -> helper.sendInfo(source, helper.getMessage(
                VeloChatXSystemMessage.STATUS_BINDING_ENTRY, source, name, state)));
        List<BindingDiagnostics.Issue> issues = serviceFactory.analyzeBindings();
        long warnings = issues.stream()
                .filter(issue -> issue.severity() == BindingDiagnostics.Severity.WARNING)
                .count();
        if (issues.isEmpty()) {
            helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.BINDING_CHECK_OK, source));
        } else {
            helper.sendInfo(source, helper.getMessage(
                    VeloChatXSystemMessage.STATUS_BINDING_ISSUES, source, issues.size(), warnings));
            for (BindingDiagnostics.Issue issue : issues) {
                helper.sendInfo(source, helper.getMessage(issue.template(), source, issue.args()));
            }
        }
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

        PlaceholderContext targetContext = helper.contextOf(target);

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

    /** Subcommand names visible to the source (permission + source type filtered). */
    private List<String> availableCommandNames(CommandSource source) {
        List<SubCommand> commands = source.hasPermission(ADMIN_PERMISSION) ? ADMIN_COMMANDS : DEFAULT_COMMANDS;
        List<String> names = commands.stream()
                .filter(command -> isAvailable(source, command))
                .map(SubCommand::name)
                .collect(Collectors.toCollection(ArrayList::new));
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
    public boolean execute(CommandSource source, String[] args) {
        // `config migration [name|all] [--dry-run]` needs four tokens; everything else stays at three.
        int maxArgs = args.length > 0 && "config".equalsIgnoreCase(args[0]) ? 4 : 3;
        if (args.length > maxArgs) {
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
        // Generic config subcommands (reload / update / diff / migration) are provided by the kernel
        CommandManager commands = serviceFactory.getKernel().getCommandManager();
        if (commands != null && commands.executeGeneric(source, subCommand, args)) {
            return true;
        }
        // Admin commands. The command itself is registered WITHOUT a Velocity-level permission
        // (see VeloChatX#registerCommands), so a known admin subcommand typed by an unauthorized
        // source must report NO_PERMISSION rather than fall through to UNKNOWN_COMMAND.
        if (ADMIN_SUBCOMMANDS.contains(subCommand)) {
            if (!source.hasPermission(ADMIN_PERMISSION)) {
                helper.sendError(source, VeloChatXCustomMessage.NO_PERMISSION);
                return false;
            }
            switch (subCommand) {
                case "show-channel-info" -> {
                    return handleShowChannelInfo(source);
                }
                case "status" -> {
                    return handleStatus(source);
                }
                case "placeholder" -> {
                    if (!source.isPlayer()) {
                        helper.sendError(source, VeloChatXCustomMessage.ONLY_IN_GAME);
                        return false;
                    }
                    return handlePlaceholder(source, args);
                }
                default -> {
                    // "config" / "reload" are kernel generic subcommands, handled above. Reaching
                    // this branch means the kernel command manager is unavailable.
                    helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
                    return false;
                }
            }
        }
        // Player commands (game only). Permission is enforced here because the registrar no longer
        // gates the command: a denial must reach the player as a message, not vanish into a
        // backend-forwarded "unknown command".
        if (PLAYER_SUBCOMMANDS.contains(subCommand)) {
            if (!source.hasPermission(PLAYER_PERMISSION)) {
                helper.sendError(source, VeloChatXCustomMessage.NO_PERMISSION);
                return false;
            }
            if (!source.isPlayer()) {
                helper.sendError(source, VeloChatXCustomMessage.ONLY_IN_GAME);
                return false;
            }
            return handlePlayerCommand(source, subCommand, args);
        }

        helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSource source, String[] args) {
        if (args.length == 0 || args.length == 1) {
            return availableCommandNames(source);
        }

        String subCommand = args[0].toLowerCase();
        // /<cmd> config <reload|update|diff|migration> [name|all] [--dry-run]
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
}
