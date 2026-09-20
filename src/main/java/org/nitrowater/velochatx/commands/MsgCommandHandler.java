package org.nitrowater.velochatx.commands;

import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.spi.CommandHandler;
import org.nitrowater.waterapi.domain.spi.CommandSource;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.velochatx.domain.server.ServerConfigKey;
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

        if (args.length < 2) {
            helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            helper.sendInfo(source, VeloChatXSystemMessage.MSG_COMMAND_FORMAT);
            return false;
        }

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
        String message = buildMessage(args);

        Optional<UPlayer> optSender = helper.toUPlayer(source);
        if (optSender.isEmpty()) {
            // Console / non-player source: the proxy acts as the sender (no profile, no block list).
            sendFromProxy(source, target, message);
            return true;
        }

        UPlayer player = optSender.get();
        PlayerService playerService = serviceFactory.getPlayerService();
        // Target blocked the sender -> silent block: sender sees "sent", target receives nothing.
        if (playerService.getOrCreateAttrs(target.getUuid()).isBlocked(player.getUuid())) {
            sendToSender(source, player, target, message);
            return true;
        }
        // Sender blocked the target -> refuse to send.
        if (playerService.getOrCreateAttrs(player.getUuid()).isBlocked(target.getUuid())) {
            helper.sendInfo(source, helper.getMessage(VeloChatXCustomMessage.HAS_BLOCK, source));
            return true;
        }
        sendToSender(source, player, target, message);
        sendToReceiver(source, target, player, message);
        return true;
    }

    /** Sender sees "you -> target"; placeholders describe the TARGET. */
    private void sendToSender(CommandSource source, UPlayer sender, UPlayer target, String message) {
        String text = helper.getMessage(VeloChatXCustomMessage.MSG_TO, source)
                .replace("{Message}", message);
        serviceFactory.getPlayerActionService().sendMessage(sender, text, contextOf(target));
    }

    /** Receiver sees "sender -> you"; placeholders describe the SENDER. */
    private void sendToReceiver(CommandSource localeSource, UPlayer receiver, UPlayer sender, String message) {
        String text = helper.getMessage(VeloChatXCustomMessage.MSG_RECEIVE, localeSource)
                .replace("{Message}", message);
        serviceFactory.getPlayerActionService().sendMessage(receiver, text, contextOf(sender));
    }

    /** Console sender: the proxy identity (server-display.proxy) replaces {Player}. */
    private void sendFromProxy(CommandSource source, UPlayer target, String message) {
        String proxyDisplay = serviceFactory.getServerService().getConfig()
                .get(ServerConfigKey.PROXY_DISPLAY);
        // Console sees "you -> target", resolved from the target's context.
        String toSender = helper.getMessage(VeloChatXCustomMessage.MSG_TO, source)
                .replace("{Message}", message);
        source.sendMessage(serviceFactory.getKernel().getPlaceholderService()
                .resolve(toSender, contextOf(target)));
        // Target sees "proxy -> you"; the console has no player profile, so only {Player} is filled.
        String toReceiver = helper.getMessage(VeloChatXCustomMessage.MSG_RECEIVE, source)
                .replace("{Message}", message)
                .replace("{Player}", proxyDisplay);
        serviceFactory.getPlayerActionService().sendMessage(target, toReceiver,
                PlaceholderContext.builder().build());
    }

    private static PlaceholderContext contextOf(UPlayer player) {
        return PlaceholderContext.builder()
                .player(player)
                .serverName(player.getCurrentServer().map(UServer::getName).orElse("unknown"))
                .build();
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
