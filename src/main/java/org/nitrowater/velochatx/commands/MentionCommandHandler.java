package org.nitrowater.velochatx.commands;

import org.nitrowater.velochatx.domain.chat.ChatConfigKey;
import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.spi.CommandHandler;
import org.nitrowater.waterapi.domain.spi.CommandSource;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.velochatx.domain.shared.CommandHelper;
import org.nitrowater.velochatx.domain.shared.ServiceFactory;
import org.nitrowater.velochatx.domain.shared.VeloChatXCustomMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXPermissions;

import java.util.List;
import java.util.Optional;

/**
 * Mention command handler for VeloChatX.
 * <p>
 * Handles: /mention (aliases: /men, /at)
 * Mentions another player with title and sound notification (silent block).
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class MentionCommandHandler implements CommandHandler {

    private final ServiceFactory serviceFactory;
    private final CommandHelper helper;

    public MentionCommandHandler(ServiceFactory serviceFactory) {
        this.serviceFactory = serviceFactory;
        this.helper = serviceFactory.getCommandHelper();
    }

    @Override
    public boolean execute(CommandSource source, String[] args) {
        if (!helper.checkPermission(source, VeloChatXPermissions.playerNodes("mention"))) {
            return false;
        }

        if (!source.isPlayer()) {
            helper.sendError(source, VeloChatXCustomMessage.ONLY_IN_GAME);
            return false;
        }

        if (args.length != 1) {
            helper.sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            helper.sendInfo(source, VeloChatXSystemMessage.MENTION_COMMAND_FORMAT);
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
            // Silent block - sender sees "mention sent", but target doesn't receive.
            // {Player} resolves from the target's context.
            serviceFactory.getPlayerActionService().sendMessage(player,
                    helper.getMessage(VeloChatXCustomMessage.MENTION_TO, source),
                    helper.contextOf(target));
            return true;
        }
        // Check if sender has blocked the target
        if (playerService.getOrCreateAttrs(player.getUuid()).isBlocked(target.getUuid())) {
            helper.sendInfo(source, helper.getMessage(VeloChatXCustomMessage.HAS_BLOCK, source));
            return true;
        }
        // Sender sees "you mentioned <target>" -> resolve {Player} from the TARGET context
        PlaceholderContext targetContext = helper.contextOf(target);
        serviceFactory.getPlayerActionService().sendMessage(player,
                helper.getMessage(VeloChatXCustomMessage.MENTION_TO, source), targetContext);

        // Target sees "<sender> mentioned you" -> resolve {Player} from the SENDER context
        PlaceholderContext senderContext = helper.contextOf(player);
        serviceFactory.getPlayerActionService().sendMessage(target,
                helper.getMessage(VeloChatXCustomMessage.MENTION_RECEIVE, source), senderContext);
        // Show title to target if enabled
        Config config = serviceFactory.getChatService().getConfig();
        if (config.getBoolean(ChatConfigKey.MENTION_SHOW_TITLE_ENABLED)) {
            String mainTitle = helper.getMessage(VeloChatXCustomMessage.MENTION_TITLE_MAIN, source);
            String subTitle = helper.getMessage(VeloChatXCustomMessage.MENTION_TITLE_SUB, source);
            int fadeIn = config.get(ChatConfigKey.MENTION_TITLE_FADE_IN);
            int stay = config.get(ChatConfigKey.MENTION_TITLE_STAY);
            int fadeOut = config.get(ChatConfigKey.MENTION_TITLE_FADE_OUT);

            String resolvedMain = serviceFactory.getKernel().getPlaceholderService()
                    .resolve(mainTitle, senderContext);
            String resolvedSub = serviceFactory.getKernel().getPlaceholderService()
                    .resolve(subTitle, senderContext);

            serviceFactory.getPlayerActionService().showTitle(target,
                    resolvedMain, resolvedSub, fadeIn, stay, fadeOut);
        }
        // Play sound to target
        serviceFactory.getPlayerActionService().playSound(
                target,
                "minecraft:block.note_block.pling"
        );

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSource source, String[] args) {
        // Never enumerate online player names for sources without the mention permission.
        return helper.playerNameTabComplete(source, args, VeloChatXPermissions.playerNodes("mention"));
    }
}
