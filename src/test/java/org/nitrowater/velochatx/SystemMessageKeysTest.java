package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards that every {@link VeloChatXSystemMessage} key actually exists in both shipped
 * {@code system-message/*.yml} templates (the counterpart of {@link ConfigKeysTest} for
 * {@code ConfigKey}), and that no orphan key lingers in the templates after a dead enum
 * constant is removed.
 */
class SystemMessageKeysTest {

    @Test
    void everySystemMessageKeyExistsInBothLanguages() throws Exception {
        for (String lang : new String[]{"en", "zh"}) {
            Map<String, Object> yml = loadYaml("system-message/" + lang + ".yml");
            for (VeloChatXSystemMessage message : VeloChatXSystemMessage.values()) {
                assertTrue(yml.containsKey(message.getKey()),
                        "missing system-message key '" + message.getKey() + "' in "
                                + lang + ".yml");
            }
        }
    }

    /** Reverse check: every non-bookkeeping yml key must still have a live enum constant. */
    @Test
    void noOrphanSystemMessageKeysInTemplates() throws Exception {
        Set<String> liveKeys = new HashSet<>();
        for (VeloChatXSystemMessage message : VeloChatXSystemMessage.values()) {
            liveKeys.add(message.getKey());
        }
        for (String lang : new String[]{"en", "zh"}) {
            Map<String, Object> yml = loadYaml("system-message/" + lang + ".yml");
            for (String key : yml.keySet()) {
                if ("config-version".equals(key)) {
                    continue;
                }
                assertTrue(liveKeys.contains(key),
                        "orphan system-message key '" + key + "' in " + lang
                                + ".yml has no VeloChatXSystemMessage constant");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(String resource) throws Exception {
        try (InputStream is = SystemMessageKeysTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(is, resource + " must be on the classpath");
            return new Yaml().load(is);
        }
    }
}
