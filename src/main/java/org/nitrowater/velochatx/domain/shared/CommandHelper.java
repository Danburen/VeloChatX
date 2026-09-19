package org.nitrowater.velochatx.domain.shared;

import lombok.Getter;
import org.nitrowater.waterapi.domain.logger.LoggerService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;
import org.nitrowater.waterapi.domain.spi.CommandSource;
import org.nitrowater.waterapi.domain.spi.PlayerActionService;
import org.nitrowater.waterapi.domain.message.MessageService;
import org.nitrowater.waterapi.common.ChatColor;
import org.nitrowater.waterapi.utils.Colors;

import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Helper service for command handlers.
 * <p>
 * Provides common utility methods for command execution,
 * message sending, and player lookup.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public class CommandHelper {

    private final MessageService messageService;
    private final PlayerActionService actionService;
    private final PlaceholderService placeholderService;
    private final WProxyServer wProxyServer;
    private final Locale defaultLocale;
    @Getter
    private final LoggerService loggerService;

    /**
     * Create a new CommandHelper.
     *
     * @param messageService    the message service for localized messages
     * @param actionService     the player action service
     * @param placeholderService the placeholder service
     * @param wProxyServer      the proxy server
     * @param defaultLocale     the default locale
     */
    public CommandHelper(MessageService messageService,
                         PlayerActionService actionService,
                         PlaceholderService placeholderService,
                         WProxyServer wProxyServer,
                         Locale defaultLocale,
                         LoggerService loggerService) {
        this.messageService = messageService;
        this.actionService = actionService;
        this.placeholderService = placeholderService;
        this.wProxyServer = wProxyServer;
        this.defaultLocale = defaultLocale;
        this.loggerService = loggerService;
    }

    // === Send methods ===

    /**
     * Send an error message (red) to the source.
     *
     * @param source   the command source
     * @param template the message template
     */
    public void sendError(CommandSource source, MessageTemplate template) {
        source.sendMessage(
                Colors.coloredText(
                        getMessage(template, source),
                        ChatColor.RED
                )
        );
    }

    /**
     * Send a success message (green) to the source.
     *
     * @param source   the command source
     * @param template the message template
     */
    public void sendSuccess(CommandSource source, MessageTemplate template) {
        source.sendMessage(
                Colors.coloredText(
                        getMessage(template, source),
                        ChatColor.GREEN)
        );
    }

    /**
     * Send an info message to the source.
     *
     * @param source  the command source
     * @param message the message to send (already resolved)
     */
    public void sendInfo(CommandSource source, String message) {
        source.sendMessage(message);
    }

    /**
     * Send an info message using MessageTemplate.
     *
     * @param source   the command source
     * @param template the message template
     */
    public void sendInfo(CommandSource source, MessageTemplate template) {
        source.sendMessage(getMessage(template, source));
    }

    /**
     * Send a formatted message with placeholder resolution.
     *
     * @param source   the command source
     * @param template the message template
     * @param player   the player for placeholder context
     */
    public void sendFormatted(CommandSource source, String template, UPlayer player) {
        PlaceholderContext context = PlaceholderContext.builder()
                .player(player)
                .serverName(player.getCurrentServer().map(s -> s.getName()).orElse("unknown"))
                .build();
        source.sendMessage(placeholderService.resolve(template, context));
    }

    // === Message get methods ===

    /**
     * Get a localized message using MessageTemplate.
     * Automatically determines the message source based on the template type.
     *
     * @param template the message template
     * @param source   the command source (for locale)
     * @return the localized message
     */
    public String getMessage(MessageTemplate template, CommandSource source) {
        Locale locale = source.getLocale().orElse(defaultLocale);
        return messageService.getMessage(template, locale.getLanguage());
    }

    /**
     * Get a localized message using MessageTemplate, with args formatted
     * Automatically determines the message source based on the template type.
     *
     * @param template the message template
     * @param source   the command source (for locale)
     * @param args     the arguments to format into the message
     * @return the localized message
     * @see #getMessage(MessageTemplate, CommandSource)
     */
    public String getMessage(MessageTemplate template, CommandSource source, Object... args) {
        Locale locale = source.getLocale().orElse(defaultLocale);
        String message = messageService.getMessage(template, locale.getLanguage());
        if (args == null || args.length == 0) {
            return message;
        }
        try {
            return String.format(message, args);
        } catch (IllegalFormatException e) {
            return message;
        }
    }

    /**
     * Get a localized message using MessageTemplate.
     *
     * @param template the message template
     * @param locale   the locale
     * @return the localized message
     */
    public String getMessage(MessageTemplate template, Locale locale) {
        return messageService.getMessage(template, locale.getLanguage());
    }

    /**
     * Send a system info message to the source using MessageTemplate.
     *
     * @param source   the command source
     * @param template the system message template
     */
    public void sendSystemInfo(CommandSource source, MessageTemplate template) {
        source.sendMessage(getMessage(template, source));
    }

    // === Player methods ===

    /**
     * Convert a CommandSource to a UPlayer.
     *
     * @param source the command source
     * @return the UPlayer, or empty if source is console
     */
    public Optional<UPlayer> toUPlayer(CommandSource source) {
        return source.getUniqueId()
                .flatMap(uuid -> wProxyServer.getPlayer(uuid));
    }

    /**
     * Find a player by name.
     *
     * @param name the player name
     * @return the player, or empty if not found
     */
    public Optional<UPlayer> getPlayer(String name) {
        return wProxyServer.getPlayer(name);
    }

    /**
     * Get all online player names.
     *
     * @return list of online player names
     */
    public List<String> getAllPlayerNames() {
        return wProxyServer.getAllOnlinePlayers().stream()
                .map(UPlayer::getLoginName)
                .toList();
    }

    /**
     * Get player names excluding the source player.
     *
     * @param source the command source
     * @return list of player names
     */
    public List<String> getPlayerNamesExcluding(CommandSource source) {
        String sourceName = source.getName();
        return wProxyServer.getAllOnlinePlayers().stream()
                .map(UPlayer::getLoginName)
                .filter(name -> !name.equals(sourceName))
                .toList();
    }

    // === Check methods ===

    /**
     * Check if the source has the required permission.
     *
     * @param source     the command source
     * @param permission the required permission
     * @return true if the source has permission
     */
    public boolean checkPermission(CommandSource source, String permission) {
        if (source.hasPermission(permission)) {
            return true;
        }
        sendError(source, VeloChatXCustomMessage.NO_PERMISSION);
        return false;
    }

    /**
     * Check if the arguments count is valid.
     *
     * @param source the command source
     * @param args   the command arguments
     * @param min    minimum argument count
     * @param max    maximum argument count
     * @return true if the argument count is valid
     */
    public boolean checkArgs(CommandSource source, String[] args, int min, int max) {
        if (args.length < min || args.length > max) {
            sendError(source, VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS);
            return false;
        }
        return true;
    }

    /**
     * Check if the source is not trying to target themselves.
     *
     * @param source     the command source
     * @param targetName the target player name
     * @return true if the target is not the source
     */
    public boolean checkNotSelf(CommandSource source, String targetName) {
        if (source.getName().equalsIgnoreCase(targetName)) {
            sendError(source, VeloChatXCustomMessage.NO_SELF_ACTION);
            return false;
        }
        return true;
    }

    public void NoticeAndTipForCorrectCommandFormat(CommandSource source, VeloChatXSystemMessage veloChatXSystemMessage) {
        source.sendMessage(
                Colors.coloredText(
                        getMessage(
                                VeloChatXCustomMessage.INCORRECT_COMMAND_ARGUMENTS,
                                source,
                                getMessage(veloChatXSystemMessage, source)
                        ),
                        ChatColor.RED
                )
        );
    }
}
