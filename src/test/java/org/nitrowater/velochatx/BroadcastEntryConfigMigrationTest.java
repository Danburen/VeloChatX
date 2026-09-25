package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.broadcast.BroadcastEntryConfigMigration;
import org.nitrowater.waterapi.domain.shared.common.SemVer;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the legacy join-leave → {@code entry-broadcast} transform.
 */
class BroadcastEntryConfigMigrationTest {

    @Test
    void movesLegacySectionsIntoEntryBroadcastAndDropsOrphans() {
        Map<String, Object> data = legacyBroadcast();

        new BroadcastEntryConfigMigration().migrate(data);

        assertFalse(data.containsKey("join-leave-broadcast"));
        assertFalse(data.containsKey("join-leave-proxy-broadcast"));

        Map<String, Object> entry = mapAt(data, "entry-broadcast");
        assertEquals(true, entry.get("log-to-console"));

        Map<String, Object> backend = mapAt(entry, "backend");
        assertEquals(true, backend.get("enable"));
        assertEquals("server", backend.get("scope"));
        assertEquals("§a(+)§r{Group}{Prefix}{Player}{Suffix} 加入了 {Server}",
                backend.get("join-message"));
        assertEquals("§c(-)§r{Group}{Prefix}{Player}{Suffix} 离开了 {Server}",
                backend.get("leave-message"));

        Map<String, Object> channel = mapAt(entry, "channel");
        assertEquals(true, channel.get("enable"));
        assertEquals(backend.get("join-message"), channel.get("join-message"));

        Map<String, Object> proxy = mapAt(entry, "proxy");
        assertEquals(true, proxy.get("enable"));
        assertEquals("proxy", proxy.get("scope"));
        assertEquals("§a[+]§r{Group}{Prefix}{Player}{Suffix} 现在加入了 {Proxy}",
                proxy.get("join-message"));
        assertEquals("§c[-]§r{Group}{Prefix}{Player}{Suffix} 现在离开了 {Proxy}",
                proxy.get("leave-message"));
    }

    @Test
    void mapsSendAllToProxyScopeAndDisablesChannel() {
        Map<String, Object> data = legacyBroadcast();
        Map<String, Object> server = mapAt(data, "join-leave-broadcast");
        server.put("send-to-all-subServer", true);
        server.put("broadcast-in-channel", false);

        new BroadcastEntryConfigMigration().migrate(data);

        assertEquals("proxy", mapAt(data, "entry-broadcast", "backend").get("scope"));
        assertEquals(false, mapAt(data, "entry-broadcast", "channel").get("enable"));
    }

    @Test
    void isIdempotentOnAlreadyMigratedConfig() {
        Map<String, Object> data = legacyBroadcast();
        BroadcastEntryConfigMigration migration = new BroadcastEntryConfigMigration();

        migration.migrate(data);
        Map<String, Object> firstBackend = mapAt(data, "entry-broadcast", "backend");
        Object firstJoin = firstBackend.get("join-message");

        migration.migrate(data);

        assertEquals(firstJoin, mapAt(data, "entry-broadcast", "backend").get("join-message"));
        assertFalse(data.containsKey("join-leave-broadcast"));
    }

    @Test
    void leavesFreshEntryBroadcastUntouched() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("join-message", "custom");
        entry.put("backend", backend);
        data.put("entry-broadcast", entry);
        Map<String, Object> before = deepCopy(data);

        new BroadcastEntryConfigMigration().migrate(data);

        assertEquals(before, data);
    }

    @Test
    void versionIsSemVerTarget210() {
        assertEquals(new SemVer(2, 1, 0), new BroadcastEntryConfigMigration().version());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        in.forEach((k, v) -> out.put(k, copy(v)));
        return out;
    }

    private static Object copy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), copy(v)));
            return out;
        }
        return value;
    }

    private static Map<String, Object> legacyBroadcast() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("enable", true);
        server.put("log-to-console", true);
        server.put("send-to-all-subServer", false);
        server.put("broadcast-in-channel", true);
        server.put("join-prefix", "§a(+)§r");
        server.put("leave-prefix", "§c(-)§r");
        server.put("player-join-message", "{Group}{Prefix}{Player}{Suffix} 加入了 {Server}");
        server.put("player-leave-message", "{Group}{Prefix}{Player}{Suffix} 离开了 {Server}");
        data.put("join-leave-broadcast", server);

        Map<String, Object> proxy = new LinkedHashMap<>();
        proxy.put("enable", true);
        proxy.put("log-to-console", true);
        proxy.put("send-to-all-subServer", true);
        proxy.put("join-prefix", "§a[+]§r");
        proxy.put("leave-prefix", "§c[-]§r");
        proxy.put("player-join-message", "{Group}{Prefix}{Player}{Suffix} 现在加入了 {Proxy}");
        proxy.put("player-leave-message", "{Group}{Prefix}{Player}{Suffix} 现在离开了 {Proxy}");
        data.put("join-leave-proxy-broadcast", proxy);

        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(Map<String, Object> root, String... path) {
        Map<String, Object> current = root;
        for (String segment : path) {
            current = (Map<String, Object>) current.get(segment);
        }
        return current;
    }
}
