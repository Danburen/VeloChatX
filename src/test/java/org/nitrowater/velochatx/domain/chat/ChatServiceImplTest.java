package org.nitrowater.velochatx.domain.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.testsupport.FakeLoggerService;
import org.nitrowater.velochatx.testsupport.FakeMessageLoggerService;
import org.nitrowater.velochatx.testsupport.FakePluginMessageService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.kernel.application.KernelConfigUpdateEvent;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.common.ChatFormatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards chat formatting (validation + reload) and the ban-word filter.
 */
class ChatServiceImplTest {

    private FakeWProxyServer proxy;
    private PlaceholderServiceImpl placeholders;
    private FakeLoggerService logger;
    private FakeMessageLoggerService messageLogger;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
        placeholders = new PlaceholderServiceImpl();
        placeholders.register("server", context -> context.getServerName().orElse(""));
        placeholders.register("player", context ->
                context.getPlayer().map(UPlayer::getLoginName).orElse(""));
        logger = new FakeLoggerService();
        messageLogger = new FakeMessageLoggerService(
                logger, new FakePluginMessageService(), placeholders);
    }

    private ChatServiceImpl service(Object... pathValuePairs) {
        return new ChatServiceImpl(TestConfigs.config(pathValuePairs), placeholders, messageLogger);
    }

    @Test
    void validFormatIsResolvedWithServerAndMessage() {
        ChatServiceImpl chat = service("chat-format", "{server} {player}: {message}");
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        assertEquals("lobby Alice: hello", chat.formatMessage("hello", alice));
    }

    @Test
    void invalidFormatFallsBackToDefaultAndWarns() {
        ChatServiceImpl chat = service("chat-format", "{player} says {message}");
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        assertTrue(messageLogger.warnings.contains("fail-parsing-chat-format-message"));
        assertEquals("lobby Alice: hello", chat.formatMessage("hello", alice));
    }

    @Test
    void placeholderCaseDoesNotMatterForValidation() {
        ChatServiceImpl chat = service("chat-format", "{SERVER} {PLAYER}: {MESSAGE}");
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        // Placeholder resolution is case-insensitive and literal text keeps its casing.
        assertEquals("lobby Alice: hello", chat.formatMessage("hello", alice));
        assertTrue(messageLogger.warnings.isEmpty());
    }

    @Test
    void customTemplateOverloadUsesPlayerServer() {
        ChatServiceImpl chat = service();
        UPlayer alice = TestPlayers.online(proxy, "Alice", "survival");

        assertEquals("[survival] Alice -> hey",
                chat.formatMessage("[{server}] {player} -> {message}", "hey", alice));
    }

    @Test
    void chatFormatModelParsesShippedAliases() {
        assertEquals(ChatFormatModel.LEGACY, service("chat-format-model", "legacy").getChatFormatModel());
        assertEquals(ChatFormatModel.MINI_MESSAGE,
                service("chat-format-model", "mini-message").getChatFormatModel());
        assertEquals(ChatFormatModel.MINI_MESSAGE,
                service("chat-format-model", "MiniMessage").getChatFormatModel());
        assertEquals(ChatFormatModel.MINI_MESSAGE,
                service("chat-format-model", "mini_message").getChatFormatModel());
        assertEquals(ChatFormatModel.NONE, service("chat-format-model", "none").getChatFormatModel());
        assertEquals(ChatFormatModel.NONE, service("chat-format-model", "mystery").getChatFormatModel());
        assertEquals(ChatFormatModel.NONE, service().getChatFormatModel());
    }

    @Test
    void banWordsAreIgnoredWhenDisabled() {
        ChatServiceImpl chat = service("ban-words.enable", false, "ban-words.words", "spam");

        assertFalse(chat.hasBannedWords("this is SPAM"));
    }

    @Test
    void banWordsAreIgnoredWhenTheListIsBlank() {
        ChatServiceImpl chat = service("ban-words.enable", true, "ban-words.words", "   ,,  ");

        assertFalse(chat.hasBannedWords("anything at all"));
    }

    @Test
    void banWordsMatchCaseInsensitivelyAndTrimEntries() {
        ChatServiceImpl chat = service(
                "ban-words.enable", true,
                "ban-words.words", " spam , BAD,, ");

        assertTrue(chat.hasBannedWords("this is SPAM"));
        assertTrue(chat.hasBannedWords("a bad word"));
        assertFalse(chat.hasBannedWords("perfectly fine"));
    }

    @Test
    void constructorPushesColorParseMode() {
        service("log-text.convert", true);
        assertEquals(Boolean.TRUE, logger.getMessageColorParseMode());

        logger.setMessageColorParseMode(null);
        service("log-text.convert", false);
        assertEquals(Boolean.FALSE, logger.getMessageColorParseMode());
    }

    @Test
    void configReloadRefreshesFormatAndColorMode() {
        ChatServiceImpl chat = service(
                "chat-format", "{server}: {message}",
                "log-text.convert", true);
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");

        chat.onEvent(new KernelConfigUpdateEvent(TestConfigs.config(
                "chat-format", "{player}@{server} {message}",
                "log-text.convert", false)));

        assertEquals("Alice@lobby hi", chat.formatMessage("hi", alice));
        assertEquals(Boolean.FALSE, logger.getMessageColorParseMode());
    }

    @Test
    void invalidReloadedFormatFallsBack() {
        ChatServiceImpl chat = service("chat-format", "{server} {player}: {message}");
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        messageLogger.warnings.clear();

        chat.onEvent(new KernelConfigUpdateEvent(TestConfigs.config(
                "chat-format", "{server} only")));

        assertTrue(messageLogger.warnings.contains("fail-parsing-chat-format-message"));
        assertEquals("lobby Alice: hi", chat.formatMessage("hi", alice));
    }
}
