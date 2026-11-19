package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.shared.VeloChatXCustomMessage;
import org.nitrowater.velochatx.domain.shared.VeloChatXSystemMessage;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the shipped locale files against drift:
 * <ul>
 *   <li>every {@link VeloChatXCustomMessage} / {@link VeloChatXSystemMessage} key exists in
 *       every locale and is non-blank;</li>
 *   <li>no orphan keys remain (they silently accumulate after renames);</li>
 *   <li>en/zh files keep the same structure for the nested config/broadcast files;</li>
 *   <li>all {@code config-version} markers agree.</li>
 * </ul>
 */
class MessageTemplateConsistencyTest {

    private static final String VERSION_KEY = "config-version";
    private static final java.util.List<String> LOCALES = java.util.List.of("en", "zh");

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String path) {
        try (InputStream stream = MessageTemplateConsistencyTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing resource: " + path);
            Object loaded = yaml().load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertTrue(loaded instanceof Map<?, ?>, "resource is not a map: " + path);
            return (Map<String, Object>) loaded;
        } catch (Exception e) {
            throw new AssertionError("failed to read " + path, e);
        }
    }

    private static Set<String> enumKeys(Set<String> keys) {
        return new TreeSet<>(keys);
    }

    @Test
    void everyCustomMessageKeyExistsInEveryLocaleWithoutOrphans() {
        Set<String> expected = enumKeys(java.util.Arrays.stream(VeloChatXCustomMessage.values())
                .map(VeloChatXCustomMessage::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        for (String locale : LOCALES) {
            Map<String, Object> messages = load("/message/" + locale + ".yml");
            Set<String> actual = new TreeSet<>(messages.keySet());
            actual.remove(VERSION_KEY);

            assertEquals(expected, actual, "message/" + locale + ".yml keys drifted from the enum");
            for (String key : expected) {
                Object value = messages.get(key);
                assertTrue(value instanceof String, "message/" + locale + ".yml: " + key + " must be text");
                assertTrue(!((String) value).isBlank(), "message/" + locale + ".yml: " + key + " is blank");
            }
        }
    }

    @Test
    void everySystemMessageKeyExistsInEveryLocaleWithoutOrphans() {
        Set<String> expected = enumKeys(java.util.Arrays.stream(VeloChatXSystemMessage.values())
                .map(VeloChatXSystemMessage::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        for (String locale : LOCALES) {
            Map<String, Object> messages = load("/system-message/" + locale + ".yml");
            Set<String> actual = new TreeSet<>(messages.keySet());
            actual.remove(VERSION_KEY);

            assertEquals(expected, actual, "system-message/" + locale + ".yml keys drifted from the enum");
            for (String key : expected) {
                Object value = messages.get(key);
                assertTrue(value instanceof String, "system-message/" + locale + ".yml: " + key + " must be text");
                assertTrue(!((String) value).isBlank(), "system-message/" + locale + ".yml: " + key + " is blank");
            }
        }
    }

    @Test
    void localizedFilesHaveIdenticalStructure() {
        assertSameShape("message", load("/message/en.yml"), load("/message/zh.yml"), "message");
        assertSameShape("system-message", load("/system-message/en.yml"),
                load("/system-message/zh.yml"), "system-message");
        assertSameShape("config", load("/config/en.yml"), load("/config/zh.yml"), "config");
        assertSameShape("broadcast", load("/broadcast/en.yml"), load("/broadcast/zh.yml"), "broadcast");
    }

    @Test
    void configVersionsAreAlignedAcrossLocales() {
        for (String base : java.util.List.of("message", "system-message", "config", "broadcast")) {
            Object en = load("/" + base + "/en.yml").get(VERSION_KEY);
            Object zh = load("/" + base + "/zh.yml").get(VERSION_KEY);

            assertTrue(en instanceof String && !((String) en).isBlank(), base + "/en.yml version missing");
            assertEquals(en, zh, base + " config-version differs between locales");
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertSameShape(String file, Object en, Object zh, String path) {
        if (en instanceof Map<?, ?> enMap) {
            assertTrue(zh instanceof Map<?, ?>, path + ": zh is not a map");
            Map<String, Object> enTyped = (Map<String, Object>) enMap;
            Map<String, Object> zhTyped = (Map<String, Object>) zh;

            assertEquals(new TreeSet<>(enTyped.keySet()), new TreeSet<>(zhTyped.keySet()),
                    path + ": key sets differ");
            for (String key : enTyped.keySet()) {
                assertSameShape(file, enTyped.get(key), zhTyped.get(key), path + "." + key);
            }
        }
    }
}
