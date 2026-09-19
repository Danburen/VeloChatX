package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.broadcast.BroadcastConfigKey;
import org.nitrowater.velochatx.domain.channel.ChannelConfigKey;
import org.nitrowater.velochatx.domain.chat.ChatConfigKey;
import org.nitrowater.velochatx.domain.server.ServerConfigKey;
import org.nitrowater.velochatx.domain.tablist.TabListConfigKey;
import org.nitrowater.waterapi.domain.shared.config.ConfigKey;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards that every {@link ConfigKey} used by the code actually exists in the
 * shipped language templates (en + zh).
 */
class ConfigKeysTest {

    @Test
    void allConfigKeysExistInLanguageTemplates() throws Exception {
        for (String lang : List.of("en", "zh")) {
            Map<String, Object> config = loadYaml("config/" + lang + ".yml");
            Map<String, Object> broadcast = loadYaml("broadcast/" + lang + ".yml");

            for (ConfigKey key : ChatConfigKey.values()) {
                assertKey(config, key, "config/" + lang + ".yml");
            }
            for (ConfigKey key : ChannelConfigKey.values()) {
                assertKey(config, key, "config/" + lang + ".yml");
            }
            for (ConfigKey key : ServerConfigKey.values()) {
                assertKey(config, key, "config/" + lang + ".yml");
            }
            for (ConfigKey key : TabListConfigKey.values()) {
                assertKey(config, key, "config/" + lang + ".yml");
            }
            for (ConfigKey key : BroadcastConfigKey.values()) {
                assertKey(broadcast, key, "broadcast/" + lang + ".yml");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String resource) throws Exception {
        try (InputStream is = ConfigKeysTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(is, resource + " must be on the classpath");
            return new Yaml().load(is);
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertKey(Map<String, Object> root, ConfigKey key, String file) {
        Object current = root;
        for (String segment : key.getKey().split("\\.")) {
            if (!(current instanceof Map)) {
                current = null;
                break;
            }
            current = ((Map<String, Object>) current).get(segment);
        }
        assertNotNull(current, "missing config key '" + key.getKey() + "' in " + file);
    }
}
