package org.nitrowater.velochatx.commands;

import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.spi.CommandHandler;
import org.nitrowater.waterapi.domain.spi.CommandSource;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.velochatx.domain.shared.CommandHelper;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.domain.shared.VeloChatXCustomMessage;

import java.util.List;
import java.util.Optional;

/**
 * Private message command handler for VeloChatX.
 * <p>
 * Handles: /vcmsg (aliases: /vctell)
 * Sends a private message to another player (silent block).
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class MsgCommandHandler implements CommandHandler {

    private final ServiceFactory serviceFactory;
    private final CommandHelper helper;

    public MsgCommandHandler(ServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
        this.helper = serviceFactory.getCommandHelper();
    }

    @Override
    public boolean execute(CommandSource source, String[] args) {
        if (!helper.checkPermission(source, "velochatx.player")) {
            return false;
        }

        if (!source.isPlayer()) {
            helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            return false;
        }

        if (args.length < 2) {
            helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            helper.sendInfo(source, VeloChatXSystemMessage.MSG_COMMAND_FORMAT);
            return false;
        }

        Optional<UPlayer> optPlayer = helper.toUPlayer(source);
        if (optPlayer.isEmpty()) {
            return false;
        }

        UPlayer player = optPlayer.get();
        String targetName = args[0];

        if (!helper.checkNotSelf(source, targetName)) {
            return false;
        }

        Optional<UPlayer> optTarget = helper.getPlayer(targetName);
        if (optTarget.isEmpty()) {
            helper.sendError(source, VeloChatXCustomMessage.FAIL_FIND_PLAYER);
            return false;
        }

        UPlayer target = optTarget.get();
        PlayerService playerService = serviceFactory.getPlayerService();
        // Check if target has blocked the sender (silent block)
        if (playerService.getOrCreateAttrs(target.getUuid()).isBlocked(player.getUuid())) {
            // Silent block - sender sees "message sent", but target doesn't receive
            sendMsgToSource(source, player, target, args);
            return true;
        }
        // Check if sender has blocked the target (prevent sending to blocked player)
        if (playerService.getOrCreateAttrs(player.getUuid()).isBlocked(target.getUuid())) {
            helper.sendInfo(source, helper.getMessage(VeloChatXCustomMessage.HAS_BLOCK, source));
            return true;
        }
        // Build message from remaining args
        String message = buildMessage(args);
        // Send messages
        PlaceholderContext ctx = PlaceholderContext.builder()
                .player(player)
                .serverName(player.getCurrentServer().map(s -> s.getName()).orElse("unknown"))
                .build();
        // Sender sees "message sent"
        serviceFactory.getPlayerActionService().sendMessage(player,
                helper.getMessage(VeloChatXCustomMessage.MSG_TO, source)
                        .replace("{Message}", message)
                        .replace("{Player}", targetName),
                ctx);
        // Receiver sees "message received" (only if not blocked)
        serviceFactory.getPlayerActionService().sendMessage(target,
                helper.getMessage(VeloChatXCustomMessage.MSG_RECEIVE, source)
                        .replace("{Message}", message)
                        .replace("{Player}", player.getLoginName()),
                PlaceholderContext.builder()
                        .player(target)
                        .serverName(target.getCurrentServer().map(s -> s.getName()).orElse("unknown"))
                        .build());

        return true;
    }

    private void sendMsgToSource(CommandSource source, UPlayer player, UPlayer target, String[] args) {
        String message = buildMessage(args);
        PlaceholderContext ctx = PlaceholderContext.builder()
                .player(player)
                .serverName(player.getCurrentServer().map(s -> s.getName()).orElse("unknown"))
                .build();

        serviceFactory.getPlayerActionService().sendMessage(player,
                helper.getMessage(VeloChatXCustomMessage.MSG_TO, source)
                        .replace("{Message}", message)
                        .replace("{Player}", target.getLoginName()),
                ctx);
    }

    private String buildMessage(String[] args) {
        StringBuilder messageBuilder = new StringBuilder(args[1]);
        for (int i = 2; i < args.length; i++) {
            messageBuilder.append(" ").append(args[i]);
        }
        return messageBuilder.toString();
    }

    @Override
    public List<String> tabComplete(CommandSource source, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            return helper.getPlayerNamesExcluding(source).stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    @Override
    public String getPermission() {
        return "velochatx.player";
    }
}
