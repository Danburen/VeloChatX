package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;

/**
 * VeloChatX custom messages, loaded from message/*.yml.
 * <p>
 * These messages are visible to players and can be customized by server admins.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public enum VeloChatXCustomMessage implements MessageTemplate {
    // Player action messages
    MENTION_TO("mention-to-message"),
    MENTION_RECEIVE("mention-receive-message"),
    MENTION_TITLE_MAIN("mention-title-show-main"),
    MENTION_TITLE_SUB("mention-title-show-sub"),
    MSG_TO("msg-to-message"),
    MSG_RECEIVE("msg-receive-message"),
    HAS_BLOCK("has-block-message"),
    HAS_UNBLOCK("has-unblock-message"),
    BAN_WORDS("ban-words-message"),
    REJECT_LIST_SHOW("reject-list-show-message"),
    EMPTY_LIST_SHOW("empty-list-show-message"),
    ENABLE_VC_CHAT("enable-vc-chat-message"),
    DISABLE_VC_CHAT("disable-vc-chat-message"),

    // System messages
    NO_SELF_ACTION("no-self-action-message"),
    FAIL_FIND_PLAYER("fail-find-player-message"),
    UNKNOWN_COMMAND("unknown-command-message"),
    ONLY_IN_GAME("only-in-game-message"),
    ONLY_IN_CONSOLE("only-in-console-message"),
    NO_PERMISSION("no-permission-message"),
    INCORRECT_COMMAND_ARGUMENTS("incorrect-command-arguments-message"),
    MESSAGE_LOCALE_CANT_LOAD("cant-load-message"),;

    private final String key;

    VeloChatXCustomMessage(String key) {
        this.key = key;
    }

    @Override
    public String getKey() {
        return key;
    }
}
