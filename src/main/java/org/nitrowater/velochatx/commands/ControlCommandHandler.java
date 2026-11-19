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
import org.nitrowater.velochatx.domain.shared.DiagnosticsService;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.domain.shared.StatusSnapshot;
import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXCustomMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXPermissions;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private static final List<String> BLOCK_ACTIONS = List.of("block", "unblock");
    private static final List<String> CONFIG_FILES = List.of("config", "message", "broadcast");
    private static final List<String> CONFIG_ACTIONS = List.of("reload", "update", "diff", "migrate");
    /** Valid config targets for the third token: a managed file name or {@code all}. */
    private static final List<String> CONFIG_TARGETS =
            Stream.concat(CONFIG_FILES.stream(), Stream.of("all")).toList();

    /**
     * The command tree — the single source of truth for dispatch, help and completion.
     * Built once in the constructor and shared with registration via {@link #buildTree()}.
     */
    private final CommandNode root;

    public ControlCommandHandler(ServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
        this.helper = serviceFactory.getCommandHelper();
        this.root = buildRoot();
    }

    /** Declare one subcommand node: tier + the permissions derived from it (see {@link VeloChatXPermissions#nodesFor}). */
    private CommandNode child(String name, CommandNode.Access access, boolean playerOnly) {
        return CommandNode.builder(name)
                .access(access)
                .permissions(VeloChatXPermissions.nodesFor(name, access))
                .playerOnly(playerOnly)
                .executor(this)
                .build();
    }

    /**
     * The full subcommand declaration (tree = table: dispatch, help and completion all read
     * it). Each command accepts its own {@code velochatx.player.<cmd>} /
     * {@code velochatx.admin.<cmd>} node, the group node, and (for player commands)
     * {@code velochatx.admin}.
     */
    private CommandNode buildRoot() {
        // Bare `/velochatx` still prints the identity / help line.
        // Pass `this` (not `this::execute`) so tabComplete is preserved.
        CommandNode.Builder root = CommandNode.builder("velochatx").executor(this);
        root.child(child("help", CommandNode.Access.OPEN, false));
        root.child(child("config", CommandNode.Access.ADMIN, false));
        root.child(child("reload", CommandNode.Access.ADMIN, false));
        root.child(child("show-channel-info", CommandNode.Access.ADMIN, false));
        root.child(child("status", CommandNode.Access.ADMIN, false));
        root.child(child("placeholder", CommandNode.Access.ADMIN, true));
        root.child(child("on", CommandNode.Access.PLAYER, true));
        root.child(child("off", CommandNode.Access.PLAYER, true));
        root.child(child("block", CommandNode.Access.PLAYER, true));
        root.child(child("unblock", CommandNode.Access.PLAYER, true));
        root.child(child("list", CommandNode.Access.PLAYER, true));
        return root.build();
    }

    /**
     * The platform-agnostic command tree, shared between registration and dispatch.
     * <p>
     * Each subcommand is a literal child carrying its access tier, permission / player-only
     * flags, so a platform (Velocity renders Brigadier {@code requires(...)}) only exposes the
     * subcommands a source is allowed to use. Every leaf shares this handler's
     * {@link #execute(CommandSource, String[])}: argument zero is the subcommand.
     * </p>
     *
     * @return the root node of the tree (cached; built once in the constructor)
     */
    public CommandNode buildTree() {
        return root;
    }

    private String pluginIdentity() {
        PluginContext context = serviceFactory.getKernel().getPluginContext();
        return context.getPluginName() + " v" + context.getMeta().getVersion();
    }

    private boolean handleHelp(CommandSource source) {
        helper.sendInfo(source, pluginIdentity());

        Set<VeloChatXSystemMessage> printed = new LinkedHashSet<>();
        // Same visibility filter as completion: only what this source may actually run.
        for (String name : availableCommandNames(source)) {
            VeloChatXSystemMessage template = switch (name) {
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

    /**
     * Report a one-shot runtime status summary: how many servers are declared (and how many of
     * them Velocity has registered), how many channels are loaded and how many broadcast messages
     * exist. The numbers come from the same {@link StatusSnapshot} the debug-level startup
     * self-check uses.
     */
    private boolean handleStatus(CommandSource source) {
        DiagnosticsService diagnostics = serviceFactory.getDiagnosticsService();
        StatusSnapshot snapshot = diagnostics.snapshot();

        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.STATUS_COMMAND_TITLE, source));
        // "Online/offline" here = declared ∩ Velocity-registered, NOT process liveness.
        // registeredCount is the raw velocity.toml [servers] size, which also exposes UNBOUND ids.
        helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.STATUS_SERVER_LINE, source,
                snapshot.getServerBound(), snapshot.getServerOnline(), snapshot.getServerOffline(),
                snapshot.getRegisteredServers().size()));
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
                VeloChatXSystemMessage.STATUS_BINDING_ENTRY, source, name, switch (state) {
                    case "BOUND" -> "§a" + state;
                    case "UNBOUND" -> "§e" + state;
                    case "PHANTOM" -> "§c" + state;
                    default -> state;
                })));
        List<BindingDiagnostics.Issue> issues = diagnostics.analyzeBindings();
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
                // /velochatx list [block]; bare `list` defaults to `block`
                if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("block"))) {
                    helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
                    helper.sendInfo(source, helper.getMessage(VeloChatXSystemMessage.LIST_COMMAND_FORMAT, source));
                    return false;
                }
                var attrs = playerService.getOrCreateAttrs(player.getUuid());
                if (attrs.getBlockedPlayers().isEmpty()) {
                    helper.sendInfo(source, helper.getMessage(VeloChatXCustomMessage.EMPTY_LIST_SHOW, source));
                    return true;
                }
                helper.sendSuccess(source, VeloChatXCustomMessage.REJECT_LIST_SHOW);
                attrs.getBlockedPlayers().forEach(uuid -> {
                    String name = serviceFactory.getWProxyServer().getPlayer(uuid)
                            .map(UPlayer::getLoginName)
                            .orElseGet(() -> playerService.getPlayerName(uuid).orElse("Unknown"));
                    helper.sendInfo(source, "§6*§r " + name);
                });
                return true;
            }
            case "block", "unblock" -> {
                if (!helper.checkArgs(source, args, 2, 2)
                        || !helper.checkNotSelf(source, args[1])) {
                    return false;
                }
                Optional<UPlayer> optTarget = helper.getPlayer(args[1]);
                if (optTarget.isEmpty()) {
                    helper.sendError(source, VeloChatXCustomMessage.FAIL_FIND_PLAYER);
                    return false;
                }
                UPlayer target = optTarget.get();
                PlaceholderContext targetContext = helper.contextOf(target);
                if (subCommand.equals("block")) {
                    playerService.blockPlayer(player.getUuid(), target.getUuid());
                    helper.sendInfo(source, serviceFactory.getKernel().getPlaceholderService()
                            .resolve(helper.getMessage(VeloChatXCustomMessage.HAS_BLOCK, source), targetContext));
                } else {
                    playerService.unblockPlayer(player.getUuid(), target.getUuid());
                    helper.sendInfo(source, serviceFactory.getKernel().getPlaceholderService()
                            .resolve(helper.getMessage(VeloChatXCustomMessage.HAS_UNBLOCK, source), targetContext));
                }
                return true;
            }
            default -> {
                helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
                return false;
            }
        }
    }

    /** Subcommand names visible to the source (permission + source type filtered). */
    private List<String> availableCommandNames(CommandSource source) {
        List<String> names = root.getChildren().stream()
                .filter(command -> command.isVisible(source))
                .map(CommandNode::getName)
                .collect(Collectors.toCollection(ArrayList::new));
        // Kernel generics not already in the tree, gated by the kernel's own any-of nodes.
        CommandManager manager = serviceFactory.getKernel().getCommandManager();
        if (manager != null) {
            for (String generic : manager.getGenericNames()) {
                if (!names.contains(generic) && manager.permits(source, generic)) {
                    names.add(generic);
                }
            }
        }
        return names;
    }

    @Override
    public boolean execute(CommandSource source, String[] args) {
        if (args.length == 0) {
            helper.sendInfo(source, pluginIdentity());
            return true;
        }
        String subCommand = args[0].toLowerCase();
        // `placeholder` takes a free-form string, so it has no token limit.
        // `config migration [name|all] [--dry-run]` needs four tokens; everything else stays at three.
        boolean freeForm = "placeholder".equals(subCommand);
        int maxArgs = "config".equals(subCommand) ? 4 : 3;
        if (!freeForm && args.length > maxArgs) {
            helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            return false;
        }
        // Help is always reachable, for every player and the console.
        if (subCommand.equals("help")) {
            return handleHelp(source);
        }
        // Generic config subcommands (reload / update / diff / migration) are provided by the kernel
        CommandManager commands = serviceFactory.getKernel().getCommandManager();
        if (commands != null && commands.executeGeneric(source, subCommand, args)) {
            return true;
        }
        // Single tree lookup: an unknown name reports UNKNOWN_COMMAND, while a KNOWN
        // subcommand typed by an unauthorized source reports NO_PERMISSION (the root command
        // is registered WITHOUT a Velocity-level permission, see VeloChatX#registerCommands),
        // never the misleading UNKNOWN_COMMAND.
        CommandNode command = root.getChild(subCommand);
        if (command == null) {
            helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
            return false;
        }
        if (!source.hasAnyPermission(command.getPermissions())) {
            helper.sendError(source, VeloChatXCustomMessage.NO_PERMISSION);
            return false;
        }
        // Player-only subcommands need an in-game source; checked once here so a denial
        // reaches the sender as a message instead of vanishing into a backend-forwarded
        // "unknown command" (the registrar no longer gates the command).
        if (command.isPlayerOnly() && !source.isPlayer()) {
            helper.sendError(source, VeloChatXCustomMessage.ONLY_IN_GAME);
            return false;
        }
        if (command.getAccess() == CommandNode.Access.OPEN) {
            // Only `help` is OPEN and it is dispatched before this lookup; anything else
            // is not wired up yet.
            helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
            return false;
        }
        if (command.getAccess() == CommandNode.Access.PLAYER) {
            return handlePlayerCommand(source, subCommand, args);
        }
        // Administrative subcommands; `placeholder`'s player-only gate already ran above.
        switch (subCommand) {
            case "show-channel-info" -> {
                helper.sendInfo(source, helper.getMessage(
                        VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_TITLE, source));
                serviceFactory.getChannelService().getAllChannels().forEach(channel ->
                        helper.sendInfo(source, helper.getMessage(
                                VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_ENTRY, source,
                                channel.getName(), channel.getDisplayName(),
                                channel.getOnlinePlayerCount())));
                return true;
            }
            case "status" -> {
                return handleStatus(source);
            }
            case "placeholder" -> {
                // The whole tail is the template: `/vcx placeholder hello {player}` must not be
                // truncated to its first token.
                if (args.length >= 2) {
                    String template = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    helper.toUPlayer(source).ifPresent(player -> {
                        String resolved = serviceFactory.getKernel().getPlaceholderService()
                                .resolve(template, PlaceholderContext.builder()
                                        .player(player)
                                        .build());
                        helper.sendInfo(source, resolved);
                    });
                }
                return true;
            }
            default -> {
                // "config" / "reload" are kernel generic subcommands, handled above. Reaching
                // this branch means the kernel command manager is unavailable.
                helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
                return false;
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSource source, String[] args) {
        if (args.length == 0) {
            return availableCommandNames(source);
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return availableCommandNames(source).stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        // /<cmd> config <reload|update|diff|migration> [name|all] [--dry-run]
        CommandNode configNode = root.getChild("config");
        if (subCommand.equals("config")
                && configNode != null && source.hasAnyPermission(configNode.getPermissions())) {
            return switch (args.length) {
                case 2 -> CONFIG_ACTIONS;
                case 3 -> CONFIG_TARGETS;
                // Only migration accepts a flag as the fourth token.
                case 4 -> {
                    String action = args[1] == null ? "" : args[1].toLowerCase(Locale.ROOT);
                    yield action.equals("migration") || action.equals("migrate")
                            ? List.of("--dry-run") : List.of();
                }
                default -> List.of();
            };
        }
        CommandNode blockNode = BLOCK_ACTIONS.contains(subCommand) ? root.getChild(subCommand) : null;
        if (args.length == 2 && blockNode != null
                && source.isPlayer() && source.hasAnyPermission(blockNode.getPermissions())) {
            return helper.getPlayerNamesExcluding(source);
        }

        // /<cmd> list <action> — only `block` exists for now
        if (args.length == 2 && subCommand.equals("list")) {
            return List.of("block");
        }

        return List.of();
    }
}
