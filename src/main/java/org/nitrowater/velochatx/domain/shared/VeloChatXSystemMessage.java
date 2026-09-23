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
    SHOW_CHANNEL_INFO_COMMAND_ENTRY("show-channel-info-command-entry"),
    // Status command
    STATUS_COMMAND_TITLE("status-command-title"),
    STATUS_SERVER_LINE("status-server-line"),
    STATUS_CHANNEL_LINE("status-channel-line"),
    STATUS_BROADCAST_LINE("status-broadcast-line"),
    // Log format
    BAN_WORDS_LOG("ban-words-log-message"),
    // Internal hints
    FAIL_PARSING_CHAT_FORMAT("fail-parsing-chat-format-message"),
    // Command help text
    MENTION_COMMAND_FORMAT("mention-command-format-message"),
    MSG_COMMAND_FORMAT("msg-command-format-message"),
    CONFIG_COMMAND_FORMAT("config-command-format-message"),
    HELP_COMMAND_FORMAT("help-command-format-message"),
    ON_COMMAND_FORMAT("on-command-format-message"),
    OFF_COMMAND_FORMAT("off-command-format-message"),
    CONTROL_COMMAND_FORMAT("control-command-format-message"),
    SHOW_CHANNEL_INFO_COMMAND_FORMAT("show-channel-info-command-format-message"),
    LIST_COMMAND_FORMAT("list-command-format-message"),
    PLACEHOLDER_COMMAND_FORMAT("placeholder-command-format-message"),
    RELOAD_COMMAND_FORMAT("reload-command-format-message"),
    STATUS_COMMAND_FORMAT("status-command-format-message"),
    MIGRATE_COMMAND_FORMAT("migrate-command-format-message"),
    // Server/channel binding diagnostics (see BindingDiagnostics)
    BINDING_UNBOUND_SERVER("binding-unbound-server"),
    BINDING_PHANTOM_SERVER("binding-phantom-server"),
    BINDING_CHANNEL_UNKNOWN_SERVER("binding-channel-unknown-server"),
    BINDING_CHANNEL_EMPTY("binding-channel-empty"),
    BINDING_CHANNEL_EMPTY_DROPPED("binding-channel-empty-dropped"),
    BINDING_SERVER_WITHOUT_CHANNEL("binding-server-without-channel"),
    BINDING_CHECK_OK("binding-check-ok"),
    BINDING_SUMMARY_WARNING("binding-summary-warning"),
    BINDING_SUMMARY_INFO("binding-summary-info"),
    BINDING_LEGACY_HINT("binding-legacy-hint"),
    STATUS_BINDING_TITLE("status-binding-title"),
    STATUS_BINDING_ENTRY("status-binding-entry"),
    STATUS_BINDING_ISSUES("status-binding-issues");

    private final String key;

    VeloChatXSystemMessage(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
