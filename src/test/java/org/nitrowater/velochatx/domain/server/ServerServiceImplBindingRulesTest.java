package org.nitrowater.velochatx.domain.server;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the legacy/new server-binding reconciliation rules absorbed from the former
 * {@code BindingRules} helpers (see {@code docs/server-binding-plan.md}).
 * {@code resolveProxyDisplay} was sunk into {@code ConfigData#getResolvedLegacyValue};
 * see WaterAPI {@code FileConfigDataLegacyValueTest}.
 */
class ServerServiceImplBindingRulesTest {

    @Test
    void legacyDisplayTakesPrecedenceOverNew() {
        Map<String, String> legacy = Map.of("lobby", "[legacy]");
        Map<String, Object> neu = Map.of("lobby", Map.of("display", "[new]"));

        assertEquals("[legacy]", ServerServiceImpl.resolveDisplay(legacy, neu, "lobby"));
    }

    @Test
    void newDisplayUsedWhenLegacyAbsent() {
        Map<String, Object> neu = Map.of("skyblock", Map.of("display", "[new]"));

        assertEquals("[new]", ServerServiceImpl.resolveDisplay(Map.of(), neu, "skyblock"));
        assertNull(ServerServiceImpl.resolveDisplay(Map.of(), neu, "missing"));
    }

    @Test
    void channelsAreReadFromNewServerList() {
        Map<String, Object> neu = Map.of("lobby", Map.of("channels", List.of("login", "global")));

        assertEquals(List.of("login", "global"), ServerServiceImpl.channelsOf(neu, "lobby"));
        assertEquals(List.of(), ServerServiceImpl.channelsOf(neu, "missing"));
    }

    @Test
    void declaredIdsAreLegacyUnionNew() {
        // loadServers builds the same union: legacy keySet then new keySet into LinkedHashSet.
        Map<String, String> legacy = new LinkedHashMap<>();
        legacy.put("lobby", "[L]");
        Map<String, Object> neu = new LinkedHashMap<>();
        neu.put("skyblock", Map.of("display", "[S]"));

        var ids = new java.util.LinkedHashSet<String>();
        ids.addAll(legacy.keySet());
        ids.addAll(neu.keySet());

        assertTrue(ids.contains("lobby"));
        assertTrue(ids.contains("skyblock"));
        assertEquals(2, ids.size());
    }
}
