package org.nitrowater.velochatx.domain.chat;

import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.logger.MessageLoggerService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.common.ChatFormatModel;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.utils.StringUtil;

import java.util.*;

/**
 * Implementation of {@link ChatService}.
 * <p>
 * This service handles chat formatting and ban words filtering.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class ChatServiceImpl implements ChatService {

    private volatile Config config;
    private final PlaceholderService placeholderService;
    private final MessageLoggerService messageLoggerService;

    // Volatile: written on config-reload threads, read on chat event threads.
    private volatile String chatFormat = "{server}{player}: {message}";

    /**
     * Create a new ChatServiceImpl.
     *
     * @param config             the config to read from
     * @param placeholderService the placeholder service for template resolution
     */
    public ChatServiceImpl(Config config, PlaceholderService placeholderService, MessageLoggerService messageLoggerService) {
        this.config = config;
        this.placeholderService = placeholderService;
        this.messageLoggerService = messageLoggerService;
        refreshChatFormat();
        applyLogColorMode();
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public void onEvent(KernelConfigUpdateEvent event) {
        this.config = event.getConfig();
        refreshChatFormat();
        applyLogColorMode();
    }

    /**
     * Re-read {@code chat-format} from config, falling back to the default when the
     * required placeholders are missing. Called on construction and on every config
     * reload so format changes apply without a restart.
     */
    private void refreshChatFormat() {
        String format = getConfig().get(ChatConfigKey.CHAT_FORMAT, "{server} {player}: {message}");
        // Placeholder keys are case-insensitive ({Server}/{server}), so compare in lower case.
        String lowerFormat = format.toLowerCase(Locale.ROOT);
        if (!(lowerFormat.contains("{server}") && lowerFormat.contains("{player}") && lowerFormat.contains("{message}"))) {
            format = "{server} {player}: {message}";
            messageLoggerService.warn(VeloChatXSystemMessage.FAIL_PARSING_CHAT_FORMAT);
        }
        this.chatFormat = format;
    }

    /**
     * Push {@code log-text.convert} into the shared logger.
     * <p>That switch decides how <b>all</b> console/log output renders Minecraft color codes:
     * {@code true} converts {@code §} codes to ANSI terminal colors (the log file stays clean
     * because Velocity strips ANSI there), {@code false} strips them so raw {@code §} codes are
     * never printed. Re-applied on every config reload.</p>
     */
    private void applyLogColorMode() {
        messageLoggerService.getLoggerService()
                .setMessageColorParseMode(getConfig().getBoolean(ChatConfigKey.LOG_CHAT_CONVERT));
    }

    @Override
    public String formatMessage(String message, UPlayer player) {
        return formatMessage(chatFormat, message, player);
    }

    @Override
    public ChatFormatModel getChatFormatModel() {
        String raw = getConfig().get(ChatConfigKey.CHAT_FORMAT_MODEL, "none");
        if (raw == null) {
            return ChatFormatModel.NONE;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "legacy" -> ChatFormatModel.LEGACY;
            case "mini-message", "minimessage", "mini_message" -> ChatFormatModel.MINI_MESSAGE;
            default -> ChatFormatModel.NONE;
        };
    }

    @Override
    public String formatMessage(String template, String message, UPlayer player) {
        String serverName = player.getCurrentServer()
                .map(UServer::getName)
                .orElse("unknown");

        PlaceholderContext context = PlaceholderContext.builder()
                .player(player)
                .serverName(serverName)
                .build();

        Map<String, String> args = new HashMap<>();
        args.put("message", message);

        return placeholderService.resolve(template, context, args);
    }

    @Override
    public boolean hasBannedWords(String message) {
        if (!getConfig().getBoolean(ChatConfigKey.BAN_WORDS_ENABLED)) {
            return false;
        }

        String banWords = getConfig().get(ChatConfigKey.BAN_WORDS_LIST);
        if (StringUtil.isBlank(banWords)) {
            return false;
        }

        // Lower-case both sides so filtering is case-insensitive; trim and drop blank
        // entries so " spam" matches and a stray ",," cannot match every message.
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        for (String bannedWord : banWords.split(",")) {
            String word = bannedWord.trim().toLowerCase(Locale.ROOT);
            if (!word.isEmpty() && lowerMessage.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
