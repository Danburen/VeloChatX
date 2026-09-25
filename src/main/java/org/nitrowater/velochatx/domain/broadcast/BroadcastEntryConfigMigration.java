package org.nitrowater.velochatx.domain.broadcast;

import org.nitrowater.waterapi.domain.config.migration.ConfigMigration;
import org.nitrowater.waterapi.domain.shared.common.SemVer;
import org.nitrowater.waterapi.utils.ObjectUtil;

import java.util.Map;

/**
 * Migrates the legacy join/leave sections to the unified {@code entry-broadcast} tree:
 * <ul>
 *   <li>{@code join-leave-broadcast.*} → {@code entry-broadcast.backend.*} (+ channel enable)</li>
 *   <li>{@code join-leave-proxy-broadcast.*} → {@code entry-broadcast.proxy.*}</li>
 * </ul>
 * Prefix and message are concatenated to match the new single-template form. The transferred
 * legacy sections are removed so the runtime reads only {@code entry-broadcast}. Sections are
 * only written with {@code putIfAbsent}, so an already-migrated file is left untouched.
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class BroadcastEntryConfigMigration implements ConfigMigration {

    /** Target: {@code entry-broadcast} (introduced in plugin 2.1.0). */
    public static final SemVer VERSION = new SemVer(2, 1, 0);

    @Override
    public SemVer version() {
        return VERSION;
    }

    @Override
    public void migrate(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        Map<String, Object> server = ObjectUtil.asMap(data.remove("join-leave-broadcast"));
        Map<String, Object> proxy = ObjectUtil.asMap(data.remove("join-leave-proxy-broadcast"));
        if (server == null && proxy == null) {
            return;
        }
        Map<String, Object> entry = ObjectUtil.ensureMap(data, "entry-broadcast");

        if (server != null) {
            if (server.get("log-to-console") != null) {
                entry.putIfAbsent("log-to-console", server.get("log-to-console"));
            }
            Map<String, Object> backend = ObjectUtil.ensureMap(entry, "backend");
            if (server.get("enable") != null) {
                backend.putIfAbsent("enable", server.get("enable"));
            }
            boolean sendAll = Boolean.TRUE.equals(server.get("send-to-all-subServer"));
            backend.putIfAbsent("scope", sendAll ? "proxy" : "server");
            String join = joined(server.get("join-prefix"), server.get("player-join-message"));
            String leave = joined(server.get("leave-prefix"), server.get("player-leave-message"));
            if (join != null) {
                backend.putIfAbsent("join-message", join);
            }
            if (leave != null) {
                backend.putIfAbsent("leave-message", leave);
            }
            // Old broadcast-in-channel shared the same templates; send-to-all overrode it.
            Map<String, Object> channel = ObjectUtil.ensureMap(entry, "channel");
            if (server.get("enable") != null) {
                channel.putIfAbsent("enable",
                        Boolean.TRUE.equals(server.get("enable"))
                                && Boolean.TRUE.equals(server.get("broadcast-in-channel"))
                                && !sendAll);
            }
            if (join != null) {
                channel.putIfAbsent("join-message", join);
            }
            if (leave != null) {
                channel.putIfAbsent("leave-message", leave);
            }
        }

        if (proxy != null) {
            if (server == null && proxy.get("log-to-console") != null) {
                entry.putIfAbsent("log-to-console", proxy.get("log-to-console"));
            }
            Map<String, Object> proxyEntry = ObjectUtil.ensureMap(entry, "proxy");
            if (proxy.get("enable") != null) {
                proxyEntry.putIfAbsent("enable", proxy.get("enable"));
            }
            proxyEntry.putIfAbsent("scope", "proxy");
            String join = joined(proxy.get("join-prefix"), proxy.get("player-join-message"));
            String leave = joined(proxy.get("leave-prefix"), proxy.get("player-leave-message"));
            if (join != null) {
                proxyEntry.putIfAbsent("join-message", join);
            }
            if (leave != null) {
                proxyEntry.putIfAbsent("leave-message", leave);
            }
        }
    }

    private static String joined(Object prefix, Object message) {
        String p = prefix == null ? "" : String.valueOf(prefix);
        String m = message == null ? "" : String.valueOf(message);
        if (p.isEmpty() && m.isEmpty()) {
            return null;
        }
        return p + m;
    }
}
