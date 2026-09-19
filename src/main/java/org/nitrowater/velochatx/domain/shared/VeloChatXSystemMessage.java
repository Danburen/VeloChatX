package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;

/**
 * VeloChatX system messages, loaded from system-message/*.yml.
 * <p>
 * These messages are built-in and not recommended for user customization.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public enum VeloChatXSystemMessage implements MessageTemplate {
    // Channel info
    SHOW_CHANNEL_INFO_COMMAND_TITLE("show-channel-info-command-title"),
    // Log format
    BAN_WORDS_LOG("ban-words-log-message"),
    // Internal hints
    USE_DEFAULT_CHAT_FORMAT("use-default-chat-format-message"),
    FAIL_PARSING_CHAT_FORMAT("fail-parsing-chat-format-message"),
    CROSSING_CHAT_DISABLE("crossing-chat-disable-message"),
    BROADCAST_DISABLE("broadcast-disable-message"),
    // Command help text
    MENTION_COMMAND_FORMAT("mention-command-format-message"),
    MSG_COMMAND_FORMAT("msg-command-format-message"),
    RELOAD_COMMAND_FORMAT("reload-command-format-message"),
    HELP_COMMAND_FORMAT("help-command-format-message"),
    ON_COMMAND_FORMAT("on-command-format-message"),
    OFF_COMMAND_FORMAT("off-command-format-message"),
    IGNORE_COMMAND_FORMAT("ignore-command-format-message"),
    REJECT_COMMAND_FORMAT("reject-command-format-message"),
    REMOVE_COMMAND_FORMAT("remove-command-format-message"),
    CONTROL_COMMAND_FORMAT("control-command-format-message"),
    SHOW_CHANNEL_INFO_COMMAND_FORMAT("show-channel-info-command-format-message"),
    LIST_COMMAND_FORMAT("list-command-format-message"),
    PLACEHOLDER_COMMAND_FORMAT("placeholder-command-format-message"),
    CONFIG_FILE_RELOAD_ERROR("config-file-reload-error");

    private final String key;

    VeloChatXSystemMessage(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
