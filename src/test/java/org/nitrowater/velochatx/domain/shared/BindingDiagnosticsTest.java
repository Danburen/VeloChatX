package org.nitrowater.velochatx.domain.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.channel.ChannelServiceImpl;
import org.nitrowater.velochatx.domain.server.ServerServiceImpl;
import org.nitrowater.velochatx.domain.shared.BindingDiagnostics.Issue;
import org.nitrowater.velochatx.domain.shared.BindingDiagnostics.Kind;
import org.nitrowater.velochatx.domain.shared.BindingDiagnostics.Severity;
import org.nitrowater.velochatx.testsupport.FakeLoggerService;
import org.nitrowater.velochatx.testsupport.FakeWProxyServer;
import org.nitrowater.velochatx.testsupport.TestConfigs;
import org.nitrowater.waterapi.domain.shared.config.Config;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior of {@link BindingDiagnostics}: which problems are reported at which severity,
 * the empty-channel folding, and how {@code report} maps issues onto the logger.
 */
class BindingDiagnosticsTest {

    private FakeWProxyServer proxy;

    @BeforeEach
    void setUp() {
        proxy = new FakeWProxyServer();
        proxy.registerServer("lobby");
        proxy.registerServer("survival");
    }

    private List<Issue> analyze(Config config) {
        ServerServiceImpl servers = new ServerServiceImpl(config, proxy);
        ChannelServiceImpl channels = new ChannelServiceImpl(config, servers);
        return BindingDiagnostics.analyze(servers, channels);
    }

    private static List<Issue> ofKind(List<Issue> issues, Kind kind) {
        return issues.stream().filter(issue -> issue.kind() == kind).toList();
    }

    @Test
    void reportsUnboundPhantomAndChannelLessServers() {
        List<Issue> issues = analyze(TestConfigs.config(
                "server.display-enable", true,
                "server.list.lobby.display", "[Lobby]",
                "server.list.lobby.channels", List.of("login"),
                "server.list.phantom.display", "[Phantom]",
                "channels.global", false,
                "channels.channel-list.login.display", "[Login]"));

        List<Issue> unbound = ofKind(issues, Kind.UNBOUND_SERVER);
        assertEquals(1, unbound.size());
        assertEquals("survival", unbound.get(0).args()[0]);
        assertEquals(Severity.INFO, unbound.get(0).severity());

        List<Issue> phantom = ofKind(issues, Kind.PHANTOM_SERVER);
        assertEquals(1, phantom.size());
        assertEquals("phantom", phantom.get(0).args()[0]);
        assertEquals(Severity.INFO, phantom.get(0).severity());

        List<Issue> withoutChannel = ofKind(issues, Kind.SERVER_WITHOUT_CHANNEL);
        assertEquals(1, withoutChannel.size());
        assertEquals("survival", withoutChannel.get(0).args()[0]);
        assertEquals(Severity.WARNING, withoutChannel.get(0).severity());

        assertEquals(3, issues.size());
    }

    @Test
    void unboundServersAreNotReportedWhenDisplayIsDisabled() {
        List<Issue> issues = analyze(TestConfigs.config(
                "server.display-enable", false,
                "channels.global", true));

        assertTrue(ofKind(issues, Kind.UNBOUND_SERVER).isEmpty());
    }

    @Test
    void legacyChannelUnknownServerIsAWarningWhenChannelsAreIsolated() {
        List<Issue> issues = analyze(TestConfigs.config(
                "server.display-enable", true,
                "channels.global", false,
                "channels.channel-list.login.display", "[Login]",
                "channels.channel-list.login.servers", List.of("lobby", "ghost")));

        List<Issue> unknown = ofKind(issues, Kind.CHANNEL_UNKNOWN_SERVER);
        assertEquals(1, unknown.size());
        assertEquals(Severity.WARNING, unknown.get(0).severity());
        assertEquals("login", unknown.get(0).args()[0]);
        assertEquals("ghost", unknown.get(0).args()[1]);
        assertEquals(VeloChatXSystemMessage.BINDING_CHANNEL_UNKNOWN_SERVER,
                unknown.get(0).template());
    }

    @Test
    void emptyChannelFoldsDroppedServerIdsIntoOneWarning() {
        List<Issue> issues = analyze(TestConfigs.config(
                "server.display-enable", true,
                "channels.global", false,
                "channels.channel-list.login.display", "[Login]",
                "channels.channel-list.login.servers", List.of("ghost")));

        List<Issue> empty = ofKind(issues, Kind.CHANNEL_EMPTY);
        assertEquals(1, empty.size());
        assertEquals(Severity.WARNING, empty.get(0).severity());
        assertEquals("login", empty.get(0).args()[0]);
        assertEquals("ghost", empty.get(0).args()[1]);
        assertEquals(VeloChatXSystemMessage.BINDING_CHANNEL_EMPTY_DROPPED, empty.get(0).template());
        assertTrue(ofKind(issues, Kind.CHANNEL_UNKNOWN_SERVER).isEmpty(),
                "dropped ids are folded into the empty-channel warning");
    }

    @Test
    void globalChannelsDowngradeUnknownServersAndSkipChannelLessWarnings() {
        List<Issue> issues = analyze(TestConfigs.config(
                "server.display-enable", true,
                "channels.global", true,
                "channels.channel-list.login.display", "[Login]",
                "channels.channel-list.login.servers", List.of("lobby", "ghost")));

        List<Issue> unknown = ofKind(issues, Kind.CHANNEL_UNKNOWN_SERVER);
        assertEquals(1, unknown.size());
        assertEquals(Severity.INFO, unknown.get(0).severity());
        assertTrue(ofKind(issues, Kind.SERVER_WITHOUT_CHANNEL).isEmpty());
    }

    @Test
    void nullServicesProduceNoIssues() {
        assertTrue(BindingDiagnostics.analyze(null, null).isEmpty());
    }

    @Test
    void reportIsNullSafeAndRoutesBySeverity() {
        BindingDiagnostics.report(null, List.of(), false, null);

        FakeLoggerService logger = new FakeLoggerService();
        BindingDiagnostics.Renderer renderer = (template, args) -> template.getKey();

        BindingDiagnostics.report(logger, List.of(), false, renderer);
        assertTrue(logger.debugMessages.contains("binding-check-ok"));
        assertTrue(logger.warningMessages.isEmpty());

        List<Issue> issues = List.of(
                new Issue(Kind.UNBOUND_SERVER, Severity.INFO, new Object[]{"survival"}),
                new Issue(Kind.PHANTOM_SERVER, Severity.WARNING, new Object[]{"phantom"}));

        BindingDiagnostics.report(logger, issues, true, renderer);

        assertTrue(logger.debugMessages.contains("binding-unbound-server"));
        assertTrue(logger.warningMessages.contains("binding-phantom-server"));
        assertTrue(logger.warningMessages.contains("binding-summary-warning"));
        assertTrue(logger.warningMessages.contains("binding-legacy-hint"));
        assertEquals(3, logger.warningMessages.size());
    }

    @Test
    void reportWithoutWarningsUsesTheInfoSummary() {
        FakeLoggerService logger = new FakeLoggerService();
        BindingDiagnostics.Renderer renderer = (template, args) -> template.getKey();

        BindingDiagnostics.report(logger, List.of(
                new Issue(Kind.UNBOUND_SERVER, Severity.INFO, new Object[]{"survival"})),
                false, renderer);

        assertTrue(logger.debugMessages.contains("binding-summary-info"));
        assertFalse(logger.debugMessages.contains("binding-summary-warning"));
        assertTrue(logger.warningMessages.isEmpty());
    }

    @Test
    void nullIssueListIsTreatedAsHealthy() {
        FakeLoggerService logger = new FakeLoggerService();

        BindingDiagnostics.report(logger, null, false, (template, args) -> template.getKey());

        assertTrue(logger.debugMessages.contains("binding-check-ok"));
    }
}
