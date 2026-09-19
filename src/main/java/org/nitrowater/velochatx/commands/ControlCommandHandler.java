package org.nitrowater.velochatx.commands;

import org.nitrowater.waterapi.domain.logger.MessageLoggerService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.shared.common.KernelMessage;
import org.nitrowater.waterapi.domain.spi.CommandHandler;
import org.nitrowater.waterapi.domain.spi.CommandSource;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.velochatx.domain.shared.CommandHelper;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXCustomMessage;

import java.io.IOException;
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
    private final MessageLoggerService messageLoggerService;

    private static final List<String> BLOCK_ACTIONS = List.of("block", "unblock");
    private static final List<String> DEFAULT_PLAYER_COMMANDS = List.of("help", "on", "off", "block", "unblock", "list");
    private static final List<String> ADMIN_COMMANDS = List.of("help", "show-channel-info", "placeholder", "on", "off", "block", "unblock", "list");
    private static final List<String> CONFIG_FILES = List.of("config", "message", "broadcast");

    public ControlCommandHandler(ServiceFactory serviceFactory, MessageLoggerService messageLoggerService) {
        this.serviceFactory = serviceFactory;
        this.helper = serviceFactory.getCommandHelper();
        this.messageLoggerService = messageLoggerService;
    }

    @Override
    public boolean execute(CommandSource source, String[] args) {
        if (args.length > 2) {
            helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            return false;
        }
        if (args.length == 0) {
            helper.sendInfo(source, serviceFactory.getKernel().getPluginContext().getPluginName() + " v" +
                    serviceFactory.getKernel().getPluginContext().getMeta().getVersion());
            return true;
        }

        String subCommand = args[0].toLowerCase();
        // Help command
        if (subCommand.equals("help")) {
            return handleHelp(source);
        }
        // Admin commands
        if (source.hasPermission("velochatx.admin")) {
            switch (subCommand) {
                case "reload" -> {
                    return handleReload(source, args);
                }
                case "show-channel-info" -> {
                    return handleShowChannelInfo(source);
                }
                case "placeholder" -> {
                    return handlePlaceholder(source, args);
                }
            }
        }

        // Player commands
        if (source.isPlayer()) {
            return handlePlayerCommand(source, subCommand, args);
        }

        helper.sendError(source, VeloChatXCustomMessage.UNKNOWN_COMMAND);
        return false;
    }

    private boolean handleHelp(CommandSource source) {
        helper.sendInfo(source, serviceFactory.getKernel().getPluginContext().getPluginName() + " v" +
                serviceFactory.getKernel().getPluginContext().getMeta().getVersion());

        List<String> commands = source.hasPermission("velochatx.admin") ? ADMIN_COMMANDS : DEFAULT_PLAYER_COMMANDS;
        for (String cmd : commands) {
            VeloChatXSystemMessage template = switch (cmd) {
                case "help" -> VeloChatXSystemMessage.HELP_COMMAND_FORMAT;
                case "show-channel-info" -> VeloChatXSystemMessage.SHOW_CHANNEL_INFO_COMMAND_FORMAT;
                case "placeholder" -> VeloChatXSystemMessage.PLACEHOLDER_COMMAND_FORMAT;
                case "on" -> VeloChatXSystemMessage.ON_COMMAND_FORMAT;
                case "off" -> VeloChatXSystemMessage.OFF_COMMAND_FORMAT;
                case "block", "unblock" -> VeloChatXSystemMessage.CONTROL_COMMAND_FORMAT;
                case "list" -> VeloChatXSystemMessage.LIST_COMMAND_FORMAT;
                default -> VeloChatXSystemMessage.HELP_COMMAND_FORMAT;
            };
            helper.sendInfo(source, helper.getMessage(template, source));
        }
        return true;
    }

    private boolean handleReload(CommandSource source, String[] args) {
        String configFile = args.length == 1 ? "all" : args[1].toLowerCase();
        try {
            switch (configFile) {
                case "all" -> serviceFactory.reloadAll();
                case "config" -> serviceFactory.getKernel().getPluginConfigService().reload();
                case "broadcast" -> serviceFactory.reloadBroadcast();
                case "message" -> {
                    var messageService = serviceFactory.getKernel().getMessageService();
                    if (messageService == null) {
                        throw new IOException("Message service is not available");
                    }
                    messageService.reload();
                }
                default -> {
                    helper.sendError(source, VeloChatXCustomMessage.INCORRECT_CONFIG_FILE);
                    return false;
                }
            }
            if ("all".equals(configFile)) {
                helper.sendSuccess(source, VeloChatXCustomMessage.CONFIG_RELOAD_COMPLETED);
            } else {
                helper.sendSuccess(source, VeloChatXCustomMessage.CONFIG_FILE_RELOAD);
            }
            return true;
        } catch (IOException e) {
            // record log
            messageLoggerService.warn(KernelMessage.CONFIG_FILE_RELOAD_ERROR_MESSAGE, configFile);
            // send error info to command sender
            String errorMsg = serviceFactory.getKernel()
                    .getPluginMessageService()
                    .getPluginMessage(VeloChatXSystemMessage.CONFIG_FILE_RELOAD_ERROR);
            helper.sendInfo(source, errorMsg);
            return false;
        }
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
            if (source.hasPermission("velochatx.admin")) {
                return ADMIN_COMMANDS;
            }
            return DEFAULT_PLAYER_COMMANDS;
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("reload") && source.hasPermission("velochatx.admin")) {
                return CONFIG_FILES;
            }
            if (BLOCK_ACTIONS.contains(subCommand)) {
                return helper.getPlayerNamesExcluding(source);
            }
        }

        return List.of();
    }

    @Override
    public String getPermission() {
        return "velochatx.player";
    }
}
