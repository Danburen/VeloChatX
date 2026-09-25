package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.shared.ServerBindingConfigMigration;
import org.nitrowater.waterapi.domain.shared.common.SemVer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Guards the legacy → {@code server.list} binding transform.
 */
class ServerBindingConfigMigrationTest {

    @Test
    @SuppressWarnings("unchecked")
    void movesLegacyDisplayAndChannelsIntoServerList() {
        Map<String, Object> data = legacyConfig();

        new ServerBindingConfigMigration().migrate(data);

        Map<String, Object> list = mapAt(data, "server", "list");
        Map<String, Object> lobby = (Map<String, Object>) list.get("lobby");
        assertEquals("[L]", lobby.get("display"));
        assertEquals(List.of("login"), lobby.get("channels"));

        // The transferred legacy keys are removed so the new representation takes effect.
        assertFalse(data.containsKey("server-display"));
        assertEquals(true, mapAt(data, "server").get("display-enable"));
        assertEquals("[P]", mapAt(data, "server").get("proxy"));
        Map<String, Object> login = (Map<String, Object>) mapAt(data, "channels", "channel-list").get("login");
        assertFalse(login.containsKey("servers"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void isIdempotent() {
        Map<String, Object> data = legacyConfig();
        ServerBindingConfigMigration migration = new ServerBindingConfigMigration();

        migration.migrate(data);
        Map<String, Object> first = mapAt(data, "server", "list");
        Object lobbyChannelsAfterFirst = ((Map<String, Object>) first.get("lobby")).get("channels");

        migration.migrate(data);

        Map<String, Object> second = mapAt(data, "server", "list");
        assertEquals(lobbyChannelsAfterFirst, ((Map<String, Object>) second.get("lobby")).get("channels"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void removesOrphanIntVersionKeyAndDeadSwitchesAndLeavesAlreadyMigratedConfigAlone() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("config-migration-version", 1);
        data.put("broadcast-enable", true);
        data.put("ban-list", "Steve");

        Map<String, Object> list = new LinkedHashMap<>();
        Map<String, Object> lobby = new LinkedHashMap<>();
        lobby.put("display", "[L]");
        lobby.put("channels", new ArrayList<>(List.of("login")));
        list.put("lobby", lobby);
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("display-enable", true);
        server.put("proxy", "[P]");
        server.put("list", list);
        data.put("server", server);

        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("display", "[§6Login Channel§r]");
        Map<String, Object> channelList = new LinkedHashMap<>();
        channelList.put("login", channel);
        Map<String, Object> channels = new LinkedHashMap<>();
        channels.put("channel-list", channelList);
        data.put("channels", channels);

        Map<String, Object> before = deepCopy(data);
        new ServerBindingConfigMigration().migrate(data);

        assertFalse(data.containsKey("config-migration-version"));
        assertFalse(data.containsKey("broadcast-enable"));
        assertFalse(data.containsKey("ban-list"));
        // Orphan cleanup only: structure beyond the orphan keys is unchanged.
        data.remove("config-migration-version");
        data.remove("broadcast-enable");
        data.remove("ban-list");
        before.remove("config-migration-version");
        before.remove("broadcast-enable");
        before.remove("ban-list");
        assertEquals(before, data);
    }

    @Test
    void versionIsSemVerTarget211() {
        assertEquals(new SemVer(2, 1, 1), new ServerBindingConfigMigration().version());
    }

    @Test
    void movesLegacyEnableIntoServerAndDropsEmptySection() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> serverDisplay = new LinkedHashMap<>();
        serverDisplay.put("enable", true);
        data.put("server-display", serverDisplay);

        new ServerBindingConfigMigration().migrate(data);

        assertFalse(data.containsKey("server-display"));
        assertEquals(true, mapAt(data, "server").get("display-enable"));
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
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            list.forEach(v -> out.add(copy(v)));
            return out;
        }
        return value;
    }

    private static Map<String, Object> legacyConfig() {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> serverDisplay = new LinkedHashMap<>();
        serverDisplay.put("enable", true);
        serverDisplay.put("proxy", "[P]");
        Map<String, Object> display = new LinkedHashMap<>();
        display.put("lobby", "[L]");
        serverDisplay.put("display", display);
        data.put("server-display", serverDisplay);

        Map<String, Object> login = new LinkedHashMap<>();
        login.put("servers", new ArrayList<>(List.of("lobby")));
        Map<String, Object> channelList = new LinkedHashMap<>();
        channelList.put("login", login);
        Map<String, Object> channels = new LinkedHashMap<>();
        channels.put("channel-list", channelList);
        data.put("channels", channels);

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
