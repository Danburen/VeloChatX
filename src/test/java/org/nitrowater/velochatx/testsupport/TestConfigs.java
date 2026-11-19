package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.config.FileConfigData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds in-memory {@link Config} instances for tests.
 * <p>Dotted paths are expanded into nested maps, matching the shape the services read:</p>
 * <pre>{@code
 * TestConfigs.config("server.display-enable", true,
 *                     "server.list.lobby.display", "[L]");
 * }</pre>
 */
public final class TestConfigs {

    private TestConfigs() {
    }

    /** Build a config from dotted path/value pairs. */
    public static Config config(Object... pathValuePairs) {
        return new Config(new FileConfigData(data(pathValuePairs)));
    }

    /** Wrap an already-built nested map in a config (defensive copy). */
    public static Config config(Map<String, Object> data) {
        return new Config(new FileConfigData(new LinkedHashMap<>(data)));
    }

    /** Build a nested map from dotted path/value pairs. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> data(Object... pathValuePairs) {
        if (pathValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("path/value pairs must be even");
        }
        Map<String, Object> root = new LinkedHashMap<>();
        for (int i = 0; i < pathValuePairs.length; i += 2) {
            String path = String.valueOf(pathValuePairs[i]);
            Object value = pathValuePairs[i + 1];
            String[] segments = path.split("\\.");
            Map<String, Object> current = root;
            for (int s = 0; s < segments.length - 1; s++) {
                Object next = current.get(segments[s]);
                if (!(next instanceof Map)) {
                    next = new LinkedHashMap<String, Object>();
                    current.put(segments[s], next);
                }
                current = (Map<String, Object>) next;
            }
            current.put(segments[segments.length - 1], value);
        }
        return root;
    }
}
