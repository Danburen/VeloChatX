package org.nitrowater.velochatx.domain.chat;

import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.shared.common.ChatFormatModel;
import org.nitrowater.waterapi.domain.shared.service.ConfigurableService;
import org.nitrowater.waterapi.domain.shared.service.EventListener;

/**
 * Service interface for chat functionality.
 * <p>
 * Handles chat formatting, crossing chat, and ban words filtering.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 * @see ChatConfigKey
 */
public interface ChatService extends ConfigurableService<ChatConfigKey>,
        EventListener<KernelConfigUpdateEvent> {

    /**
     * Format a chat message with player and server placeholders.
     * <p>
     * Resolves placeholders like {player}, {server}, {channel} from context,
     * then replaces {message} with the actual chat message.
     * </p>
     *
     * @param message the raw chat message
     * @param player  the player who sent the message
     * @return the formatted message
     */
    String formatMessage(String message, UPlayer player);

    /**
     * Format a chat message with a custom template.
     *
     * @param template the format template (e.g., "{server} {player}: {message}")
     * @param message  the raw chat message
     * @param player   the player who sent the message
     * @return the formatted message
     */
    String formatMessage(String template, String message, UPlayer player);

    /**
     * Get the configured chat format model.
     *
     * @return the chat format model (never null, defaults to {@link ChatFormatModel#NONE})
     */
    ChatFormatModel getChatFormatModel();

    /**
     * Check if a message contains any banned words.
     *
     * @param message the message to check
     * @return {@code true} if the message contains banned words
     */
    boolean hasBannedWords(String message);
}
