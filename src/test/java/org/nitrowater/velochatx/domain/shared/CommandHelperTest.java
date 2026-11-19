package org.nitrowater.velochatx.domain.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.testsupport.FakeCommandSource;
import org.nitrowater.velochatx.testsupport.FakeMessageService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestPlayers;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderContext;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderServiceImpl;
import org.nitrowater.waterapi.domain.shared.common.MessageTemplate;
import org.nitrowater.waterapi.domain.spi.CommandSource;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards {@link CommandHelper}: locale/format resolution, colored output, permission and
 * argument gates, player lookup and tab-completion privacy.
 */
class CommandHelperTest {

    private static final MessageTemplate GREETING = () -> "greeting-message";
    private static final MessageTemplate ERROR = () -> "error-message";
    private static final MessageTemplate SUCCESS = () -> "success-message";

    private FakeWProxyServer proxy;
    private PlaceholderServiceImpl placeholders;
    private FakeMessageService messages;
    private CommandHelper helper;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
        placeholders = new PlaceholderServiceImpl();
        placeholders.register("player", context ->
                context.getPlayer().map(UPlayer::getLoginName).orElse(""));
        placeholders.register("server", context -> context.getServerName().orElse(""));

        messages = new FakeMessageService()
                .put("en", "greeting-message", "Hello %s")
                .put("zh", "greeting-message", "你好 %s")
                .put("error-message", "Oops")
                .put("success-message", "Yay")
                .put("no-permission-message", "No permission")
                .put("incorrect-command-arguments-message", "Bad arguments")
                .put("no-self-action-message", "Cannot target yourself");

        helper = new CommandHelper(messages, placeholders, proxy, Locale.ENGLISH);
    }

    @Test
    void messageUsesSourceLocaleAndFallsBackToDefault() {
        FakeCommandSource chinese = FakeCommandSource.player(
                "Alice", TestPlayers.uuidOf("Alice"), Locale.CHINESE);

        assertEquals("你好 Alice", helper.getMessage(GREETING, chinese, "Alice"));
        assertEquals("Hello Alice", helper.getMessage(GREETING, FakeCommandSource.console(), "Alice"));
        assertEquals("Hello %s", helper.getMessage(GREETING, FakeCommandSource.console()));
    }

    @Test
    void invalidFormatArgumentsReturnTheRawMessage() {
        messages.put("en", "broken-message", "Broken %d");
        MessageTemplate broken = () -> "broken-message";

        assertEquals("Broken %d",
                helper.getMessage(broken, FakeCommandSource.console(), "not-a-number"));
    }

    @Test
    void sendErrorAndSuccessApplyTheSemanticColors() {
        FakeCommandSource source = FakeCommandSource.console();

        helper.sendError(source, ERROR);
        helper.sendSuccess(source, SUCCESS);

        assertEquals("§cOops§r", source.messages.get(0));
        assertEquals("§aYay§r", source.messages.get(1));
    }

    @Test
    void sendInfoSupportsRawAndTemplatedMessages() {
        FakeCommandSource source = FakeCommandSource.console();

        helper.sendInfo(source, "plain");
        helper.sendInfo(source, SUCCESS);

        assertEquals(List.of("plain", "Yay"), source.messages);
    }

    @Test
    void permissionGateIsAnyOfAndUnrestrictedWhenEmpty() {
        FakeCommandSource source = FakeCommandSource.player(
                "Alice", TestPlayers.uuidOf("Alice"), Locale.ENGLISH, "velochatx.player.mention");

        assertTrue(helper.checkPermission(source, List.of()));
        assertTrue(helper.checkPermission(source, List.of("velochatx.player.mention")));
        assertTrue(helper.checkPermission(source, List.of("missing", "velochatx.player.mention")));
        assertFalse(helper.checkPermission(source, List.of("missing")));
        assertEquals(List.of("§cNo permission§r"), source.messages);
    }

    @Test
    void argumentCountGateReportsIncorrectArguments() {
        FakeCommandSource source = FakeCommandSource.console();

        assertTrue(helper.checkArgs(source, new String[]{"a"}, 1, 2));
        assertFalse(helper.checkArgs(source, new String[]{}, 1, 2));
        assertFalse(helper.checkArgs(source, new String[]{"a", "b", "c"}, 1, 2));
        assertEquals(2, source.messages.size());
        assertTrue(source.messages.stream().allMatch(message -> message.contains("Bad arguments")));
    }

    @Test
    void selfTargetingIsCaseInsensitive() {
        FakeCommandSource source = FakeCommandSource.player(
                "Alice", TestPlayers.uuidOf("Alice"), Locale.ENGLISH);

        assertFalse(helper.checkNotSelf(source, "alice"));
        assertTrue(helper.checkNotSelf(source, "Bob"));
        assertEquals(1, source.messages.size());
    }

    @Test
    void playerLookupResolvesOnlinePlayersOnly() {
        FakeCommandSource source = FakeCommandSource.player(
                "Alice", TestPlayers.uuidOf("Alice"), Locale.ENGLISH);
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        TestPlayers.online(proxy, "Bob", "lobby");
        TestPlayers.online(proxy, "Carol", "lobby");

        assertEquals(Optional.of(alice), helper.toUPlayer(source));
        assertEquals(Optional.of(alice), helper.getPlayer("alice"));
        assertTrue(helper.getPlayer("missing").isEmpty());
        assertTrue(helper.toUPlayer(FakeCommandSource.console()).isEmpty());

        assertEquals(List.of("Bob", "Carol"), helper.getPlayerNamesExcluding(source));
    }

    @Test
    void tabCompletionRequiresPermissionAndFiltersByPrefix() {
        FakeCommandSource source = FakeCommandSource.player(
                "Alice", TestPlayers.uuidOf("Alice"), Locale.ENGLISH);
        TestPlayers.online(proxy, "Alice", "lobby");
        TestPlayers.online(proxy, "Bob", "lobby");
        TestPlayers.online(proxy, "Bobby", "lobby");
        TestPlayers.online(proxy, "Carol", "lobby");

        assertTrue(helper.playerNameTabComplete(source, new String[]{"b"},
                List.of("velochatx.player.mention")).isEmpty());

        source.grant("velochatx.player.mention");
        List<String> permissions = List.of("velochatx.player.mention");
        assertEquals(List.of("Bob", "Bobby"),
                helper.playerNameTabComplete(source, new String[]{"b"}, permissions));
        assertEquals(List.of("Bob", "Bobby", "Carol"),
                helper.playerNameTabComplete(source, new String[]{}, permissions));
        assertTrue(helper.playerNameTabComplete(source, new String[]{"b", "x"}, permissions).isEmpty());
        assertEquals(List.of("Bob", "Bobby", "Carol"),
                helper.playerNameTabComplete(source, new String[]{}, List.of()));
    }

    @Test
    void contextOfCarriesTheCurrentServerOrUnknown() {
        UPlayer alice = TestPlayers.online(proxy, "Alice", "lobby");
        UPlayer offline = TestPlayers.offline(proxy, "Ghost");

        PlaceholderContext online = helper.contextOf(alice);
        PlaceholderContext gone = helper.contextOf(offline);

        assertEquals("Alice", placeholders.resolve("{player}", online));
        assertEquals("lobby", placeholders.resolve("{server}", online));
        assertEquals("unknown", placeholders.resolve("{server}", gone));
    }
}
