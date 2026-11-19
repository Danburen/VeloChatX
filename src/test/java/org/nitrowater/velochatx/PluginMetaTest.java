package org.nitrowater.velochatx;

import com.velocitypowered.api.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the Velocity descriptor against the {@link Plugin} annotation and the Gradle
 * version, so a release cannot ship mismatched metadata.
 */
class PluginMetaTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pluginYml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        try (InputStream stream = PluginMetaTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream, "plugin.yml is missing from the jar resources");
            Object loaded = new Yaml(new SafeConstructor(options))
                    .load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertTrue(loaded instanceof Map<?, ?>, "plugin.yml is not a map");
            return (Map<String, Object>) loaded;
        } catch (Exception e) {
            throw new AssertionError("failed to read plugin.yml", e);
        }
    }

    @Test
    void pluginYmlMatchesThePluginAnnotation() {
        Plugin annotation = VeloChatX.class.getAnnotation(Plugin.class);
        assertNotNull(annotation, "VeloChatX must keep its @Plugin annotation");

        Map<String, Object> yml = pluginYml();

        assertEquals(annotation.name(), yml.get("name"));
        assertEquals(annotation.version(), yml.get("version"));
        assertEquals(VeloChatX.class.getName(), yml.get("main"));
        assertEquals(annotation.id(), annotation.id().toLowerCase(),
                "Velocity plugin ids must be lowercase");
        assertTrue(Arrays.asList(annotation.authors()).stream()
                        .anyMatch(author -> author.equalsIgnoreCase(String.valueOf(yml.get("author")))),
                "plugin.yml author must match one of the @Plugin authors");
    }

    @Test
    void pluginVersionMatchesTheGradleVersion() throws Exception {
        Path buildFile = Path.of("build.gradle");
        assertTrue(Files.exists(buildFile), "build.gradle must be resolvable from the test working directory");
        String gradle = Files.readString(buildFile, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("^version\\s*=\\s*'([^']+)'", Pattern.MULTILINE)
                .matcher(gradle);
        assertTrue(matcher.find(), "build.gradle must declare a quoted project version");

        String gradleVersion = matcher.group(1);
        assertTrue(!gradleVersion.isBlank(), "Gradle version must not be blank");
        assertEquals(gradleVersion, pluginYml().get("version"),
                "plugin.yml version must follow build.gradle");
        assertEquals(gradleVersion, VeloChatX.class.getAnnotation(Plugin.class).version(),
                "@Plugin version must follow build.gradle");
    }

    @Test
    void descriptorNamesARealPluginMainClass() throws Exception {
        String main = String.valueOf(pluginYml().get("main"));
        Class<?> type = Class.forName(main);

        assertEquals(VeloChatX.class, type);
        assertTrue(type.getAnnotation(Plugin.class) != null);
    }
}
