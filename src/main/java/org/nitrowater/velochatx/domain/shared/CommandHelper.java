package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.waterapi.domain.message.MessageService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.model.UServer;
import org.nitrowater.waterapi.domain.model.WProxyServer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;
import org.nitrowater.waterapi.domain.spi.CommandSource;
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
    private final PlaceholderService placeholderService;
    private final WProxyServer wProxyServer;
    private final Locale defaultLocale;

    /**
     * Create a new CommandHelper.
     *
     * @param messageService     the message service for localized messages
     * @param placeholderService the placeholder service
     * @param wProxyServer       the proxy server
     * @param defaultLocale      the default locale
     */
    public CommandHelper(MessageService messageService,
                         PlaceholderService placeholderService,
                         WProxyServer wProxyServer,
                         Locale defaultLocale) {
        this.messageService = messageService;
        this.placeholderService = placeholderService;
        this.wProxyServer = wProxyServer;
        this.defaultLocale = defaultLocale;
    }

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
     * Build a placeholder context for a player, carrying the player's current server
     * name so that {@code {server}} resolves.
     *
     * @param player the player
     * @return the placeholder context
     */
    public PlaceholderContext contextOf(UPlayer player) {
        return PlaceholderContext.builder()
                .player(player)
                .serverName(player.getCurrentServer().map(UServer::getName).orElse("unknown"))
                .build();
    }

    // === Message get methods ===

    /**
     * Get a localized message using MessageTemplate, with optional format args.
     * Automatically determines the message source based on the template type.
     *
     * @param template the message template
     * @param source   the command source (for locale)
     * @param args     the arguments to format into the message (may be empty)
     * @return the localized message
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
     * Any-of permission gate: an empty list is unrestricted (handled by {@code hasAnyPermission}).
     *
     * @return true when allowed, otherwise sends NO_PERMISSION and returns false
     */
    public boolean checkPermission(CommandSource source, List<String> permissions) {
        if (source.hasAnyPermission(permissions)) {
            return true;
        }
        sendError(source, VeloChatXCustomMessage.NO_PERMISSION);
        return false;
    }

    /**
     * Player-name completion shared by {@code /mention} and {@code /vcmsg}: never enumerate
     * online names for an unauthorized source.
     */
    public List<String> playerNameTabComplete(CommandSource source, String[] args, List<String> permissions) {
        if (!source.hasAnyPermission(permissions)) {
            return List.of();
        }
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            return getPlayerNamesExcluding(source).stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        return List.of();
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
}
