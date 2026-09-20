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
        if (!helper.checkPermission(source, "velochatx.player")) {
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
            // Silent block - sender sees "mention sent", but target doesn't receive
            sendMentionToSource(source, player, target);
            return true;
        }
        // Check if sender has blocked the target
        if (playerService.getOrCreateAttrs(player.getUuid()).isBlocked(target.getUuid())) {
            helper.sendInfo(source, helper.getMessage(VeloChatXCustomMessage.HAS_BLOCK, source));
            return true;
        }
        // Sender sees "you mentioned <target>" -> resolve {Player} from the TARGET context
        PlaceholderContext targetContext = PlaceholderContext.builder()
                .player(target)
                .serverName(target.getCurrentServer().map(s -> s.getName()).orElse("unknown"))
                .build();
        serviceFactory.getPlayerActionService().sendMessage(player,
                helper.getMessage(VeloChatXCustomMessage.MENTION_TO, source), targetContext);

        // Target sees "<sender> mentioned you" -> resolve {Player} from the SENDER context
        PlaceholderContext senderContext = PlaceholderContext.builder()
                .player(player)
                .serverName(player.getCurrentServer().map(s -> s.getName()).orElse("unknown"))
                .build();
        serviceFactory.getPlayerActionService().sendMessage(target,
                helper.getMessage(VeloChatXCustomMessage.MENTION_RECEIVE, source), senderContext);
        // Show title to target if enabled
        if (serviceFactory.getKernel().getPluginConfigService().getConfigData()
                .getBoolean("mention-show-title.enable", false)) {
            String mainTitle = helper.getMessage(VeloChatXCustomMessage.MENTION_TITLE_MAIN, source);
            String subTitle = helper.getMessage(VeloChatXCustomMessage.MENTION_TITLE_SUB, source);
            int fadeIn = serviceFactory.getKernel().getPluginConfigService().getConfigData()
                    .get("mention-show-title.time.fade-in", 10);
            int stay = serviceFactory.getKernel().getPluginConfigService().getConfigData()
                    .get("mention-show-title.time.stay", 40);
            int fadeOut = serviceFactory.getKernel().getPluginConfigService().getConfigData()
                    .get("mention-show-title.time.fade-out", 10);

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

    private void sendMentionToSource(CommandSource source, UPlayer player, UPlayer target) {
        // Sender sees "you mentioned <target>"
        PlaceholderContext targetContext = PlaceholderContext.builder()
                .player(target)
                .serverName(target.getCurrentServer().map(s -> s.getName()).orElse("unknown"))
                .build();

        serviceFactory.getPlayerActionService().sendMessage(player,
                helper.getMessage(VeloChatXCustomMessage.MENTION_TO, source), targetContext);
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
