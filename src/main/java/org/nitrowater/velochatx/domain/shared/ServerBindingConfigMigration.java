package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.waterapi.domain.config.migration.ConfigMigration;
import org.nitrowater.waterapi.domain.shared.common.SemVer;
import org.nitrowater.waterapi.utils.ObjectUtil;

import java.util.List;
import java.util.Map;

/**
 * Migrates the legacy server bindings to the new {@code server} representation:
 * <ul>
 *   <li>{@code server-display.display.<id>} → {@code server.list.<id>.display}</li>
 *   <li>{@code server-display.proxy} → {@code server.proxy}</li>
 *   <li>{@code server-display.enable} → {@code server.display-enable}</li>
 *   <li>{@code channels.channel-list.<name>.servers[]} → {@code server.list.<id>.channels[]}</li>
 * </ul>
 * The transferred legacy keys are removed so the new representation takes effect (the compat
 * layer prefers legacy keys while they exist). Sections are only created when a value is
 * actually written, so an already-migrated config is left untouched. After the transfer an
 * empty {@code server-display} section is dropped entirely. Orphaned bookkeeping / removed
 * switches ({@code config-migration-version}, {@code broadcast-enable}, {@code ban-list})
 * are cleaned up.
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class ServerBindingConfigMigration implements ConfigMigration {

    /**
     * Target: {@code server.list} + {@code server.display-enable} (plugin config-version 2.1.1).
     * Bumped from 2.1.0 so files already stamped 2.1.0 re-run and pick up the enable move.
     */
    public static final SemVer VERSION = new SemVer(2, 1, 1);

    @Override
    public SemVer version() {
        return VERSION;
    }

    @Override
    public void migrate(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        // Pre-SemVer bookkeeping used an int key; drop it so it never lingers as an orphan
        // (current scheme records progress in config-version, see ConfigMigrationManager).
        data.remove("config-migration-version");
        // 2.0.1 switches removed from shipped config with no runtime readers.
        data.remove("broadcast-enable");
        data.remove("ban-list");

        Map<String, Object> list = null;
        // server-display.display.<id> -> server.list.<id>.display
        Map<String, Object> displaySection = ObjectUtil.asMap(data.get("server-display"));
        if (displaySection != null) {
            Map<String, Object> displayMap = ObjectUtil.asMap(displaySection.get("display"));
            if (displayMap != null && !displayMap.isEmpty()) {
                list = ObjectUtil.ensurePath(data, "server", "list");
                for (Map.Entry<?, ?> entry : displayMap.entrySet()) {
                    String id = String.valueOf(entry.getKey());
                    ObjectUtil.ensureMap(list, id).putIfAbsent("display", entry.getValue());
                }
                displaySection.remove("display");
            }
            // server-display.proxy -> server.proxy (new-style proxy display)
            if (displaySection.get("proxy") != null) {
                ObjectUtil.ensureMap(data, "server")
                        .putIfAbsent("proxy", displaySection.get("proxy"));
                displaySection.remove("proxy");
            }
            // server-display.enable -> server.display-enable (feature toggle)
            if (displaySection.get("enable") != null) {
                ObjectUtil.ensureMap(data, "server")
                        .putIfAbsent("display-enable", displaySection.get("enable"));
                displaySection.remove("enable");
            }
            // Drop the section once display/proxy/enable have all been transferred.
            if (displaySection.isEmpty()) {
                data.remove("server-display");
            }
        }
        // channels.channel-list.<name>.servers -> server.list.<id>.channels
        Map<String, Object> channelList = ObjectUtil.mapAt(data, "channels", "channel-list");
        if (channelList != null) {
            for (Map.Entry<?, ?> channelEntry : channelList.entrySet()) {
                String channelName = String.valueOf(channelEntry.getKey());
                Map<String, Object> channel = ObjectUtil.asMap(channelEntry.getValue());
                if (channel == null) {
                    continue;
                }
                List<Object> servers = ObjectUtil.asList(channel.get("servers"));
                if (servers == null) {
                    continue;
                }
                if (list == null) {
                    list = ObjectUtil.ensurePath(data, "server", "list");
                }
                for (Object server : servers) {
                    if (server == null) {
                        continue;
                    }
                    List<Object> channelsOfServer =
                            ObjectUtil.ensureList(
                                    ObjectUtil.ensureMap(list, String.valueOf(server)),
                                    "channels"
                            );
                    if (!channelsOfServer.contains(channelName)) {
                        channelsOfServer.add(channelName);
                    }
                }
                channel.remove("servers");
            }
        }
    }
}
