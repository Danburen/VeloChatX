package org.nitrowater.velochatx.mapper.migration;

import org.nitrowater.waterapi.domain.infra.io.database.migration.AbstractMigration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Initial migration: Create player-related tables.
 *
 * @since 2.1.0
 * @author Danburen
 */
public class V001__CreatePlayerTables extends AbstractMigration {

    public V001__CreatePlayerTables() {
        super("001", "Create player tables");
    }

    @Override
    public void up(Connection conn) throws SQLException {
        // Player table
        createTable(conn, "player",
                "uuid TEXT PRIMARY KEY",
                "name TEXT NOT NULL",
                "is_deleted INTEGER DEFAULT 0",
                "player_ind INTEGER NOT NULL AUTO_INCREMENT"
        );
        // Player status table
        createTable(conn, "player_status",
                "uuid TEXT PRIMARY KEY",
                "is_chat_offline INTEGER DEFAULT 0",
                "first_join_time TIMESTAMP NOT NULL",
                "left_time TIMESTAMP DEFAULT NULL",
                "FOREIGN KEY (uuid) REFERENCES player(uuid) ON DELETE CASCADE ON UPDATE CASCADE"
        );
        // Player ban list table
        createTable(conn, "player_ban_list",
                "uuid TEXT PRIMARY KEY",
                "ignore_list TEXT DEFAULT NULL",
                "reject_list TEXT DEFAULT NULL",
                "FOREIGN KEY (uuid) REFERENCES player(uuid) ON DELETE CASCADE ON UPDATE CASCADE"
        );
        // Create indexes for performance
        createIndex(conn, "idx_player_name", "player", "name");
        createIndex(conn, "idx_player_status_uuid", "player_status", "uuid");
    }
}
