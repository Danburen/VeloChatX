package org.nitrowater.velochatx.domain.server;

import org.nitrowater.waterapi.domain.shared.config.Config;
import org.nitrowater.waterapi.domain.shared.config.ConfigKey;

import java.util.Map;

/**
 * Configuration keys for server display name settings.
 * <p>
 * These keys correspond to entries in {@code config.yml} under the
 * {@code server} section (legacy {@code server-display.*} keys are dual-read).
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public enum ServerConfigKey implements ConfigKey {
    /** Legacy display toggle ({@code server-display.enable}). Still read first when present. */
    LEGACY_DISPLAY_ENABLED("server-display.enable", false),
    /** Whether server display name feature is enabled ({@code server.display-enable}). */
    DISPLAY_ENABLED("server.display-enable", false),
    /** Legacy proxy server display name ({@code server-display.proxy}). Still read first when present. */
    PROXY_DISPLAY("server-display.proxy", "[§5Proxy Server§r]"),
    DISPLAY_SERVER_DISPLAY("server-display.display", Map.<String, String>of()),
    /**
     * New-style server binding list: {@code server.list.<id>.{display,channels}}.
     * <p>The legacy {@link #DISPLAY_SERVER_DISPLAY} is still read and takes precedence per id;
     * see {@code docs/server-binding-plan.md}.</p>
     */
    SERVER_LIST("server.list", Map.<String, Object>of()),
    /**
     * New-style proxy display: {@code server.proxy}. Dual-read with legacy
     * {@link #PROXY_DISPLAY} (legacy wins while present); see {@code docs/server-binding-plan.md}.
     */
    SERVER_PROXY("server.proxy", "[§5Proxy Server§r]");

    private final String key;
    private final Object defaultValue;

    ServerConfigKey(String key, Object defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * Effective display-toggle value: legacy {@code server-display.enable} wins while present,
     * otherwise {@code server.display-enable}, otherwise the enum default ({@code false}).
     *
     * @param config the main config view
     * @return {@code true} when the display feature should run
     */
    public static boolean isDisplayEnabled(Config config) {
        return Boolean.TRUE.equals(config.getConfigData().getResolvedLegacyValue(
                LEGACY_DISPLAY_ENABLED.getKey(),
                DISPLAY_ENABLED.getKey(),
                (Boolean) DISPLAY_ENABLED.getDefaultValue()));
    }
}
