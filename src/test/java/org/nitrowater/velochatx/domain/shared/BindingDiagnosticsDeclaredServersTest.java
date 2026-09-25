package org.nitrowater.velochatx.domain.shared;

import org.junit.jupiter.api.Test;
import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.config.FileConfigData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dual-read tests for {@link BindingDiagnostics#declaredChannelServers}: the raw server ids a
 * channel declares, read either from the legacy {@code channels.channel-list.<n>.servers} list
 * or reverse-looked-up from the post-migration {@code server.list.<id>.channels}.
 * <p>The resolved {@code Channel#getServers()} list has already dropped unknown entries, so
 * diagnostics must read the RAW config to surface them.</p>
 */
class BindingDiagnosticsDeclaredServersTest {

    private static Config config(Map<String, Object> data) {
        return Config.fromConfigData(new FileConfigData(data));
    }

    @Test
    void nullSafeOnMissingConfig() {
        assertTrue(BindingDiagnostics.declaredChannelServers(null, "lobby").isEmpty());
        assertTrue(BindingDiagnostics.declaredChannelServers(config(Map.of()), null).isEmpty());
    }

    @Test
    void legacyMembershipReadsChannelServersList() {
        Map<String, Object> channelList = new HashMap<>();
        channelList.put("lobby", Map.of("servers", List.of("lobby", "server1")));
        channelList.put("survive", Map.of("servers", List.of("server2")));
        Map<String, Object> data = new HashMap<>();
        data.put("channels", Map.of("channel-list", channelList));
        // server.list is present but must be IGNORED while any channel has a non-empty servers list.
        data.put("server", Map.of("list", Map.of(
                "lobby", Map.of("channels", List.of("wrong-channel")))));

        Config config = config(data);
        assertEquals(List.of("lobby", "server1"),
                BindingDiagnostics.declaredChannelServers(config, "lobby"));
        assertEquals(List.of("server2"),
                BindingDiagnostics.declaredChannelServers(config, "survive"));
        assertTrue(BindingDiagnostics.declaredChannelServers(config, "unknown").isEmpty());
    }

    @Test
    void legacySkipsBlankServerIds() {
        Map<String, Object> channelList = Map.of(
                "lobby", Map.of("servers", Arrays.asList("lobby", "", "  ", null, "server1")));
        Config config = config(Map.of("channels", Map.of("channel-list", channelList)));

        assertEquals(List.of("lobby", "server1"),
                BindingDiagnostics.declaredChannelServers(config, "lobby"));
    }

    @Test
    void migratedConfigReverseLooksUpFromServerList() {
        // Post-migration shape: channels have no servers list; membership lives on server.list.
        // LinkedHashMap keeps declaration order so the reverse-lookup order is deterministic.
        Map<String, Object> channelList = new LinkedHashMap<>();
        channelList.put("lobby", Map.of("display", "Lobby"));
        channelList.put("survive", Map.of("display", "Survive"));
        Map<String, Object> serverList = new LinkedHashMap<>();
        serverList.put("lobby", Map.of("channels", List.of("lobby")));
        serverList.put("server1", Map.of("channels", List.of("lobby", "survive")));
        serverList.put("server2", Map.of("channels", List.of("survive")));
        serverList.put("orphan", Map.of("display", "no channels"));
        Config config = config(Map.of(
                "channels", Map.of("channel-list", channelList),
                "server", Map.of("list", serverList)));

        assertEquals(List.of("lobby", "server1"),
                BindingDiagnostics.declaredChannelServers(config, "lobby"));
        assertEquals(List.of("server1", "server2"),
                BindingDiagnostics.declaredChannelServers(config, "survive"));
        assertTrue(BindingDiagnostics.declaredChannelServers(config, "unknown").isEmpty());
    }

    @Test
    void migratedConfigWithNoServerListIsEmpty() {
        Map<String, Object> channelList = Map.of("lobby", Map.of("display", "Lobby"));
        Config config = config(Map.of("channels", Map.of("channel-list", channelList)));

        assertTrue(BindingDiagnostics.declaredChannelServers(config, "lobby").isEmpty());
    }
}
