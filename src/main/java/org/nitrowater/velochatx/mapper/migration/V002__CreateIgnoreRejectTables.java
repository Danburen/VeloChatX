package org.nitrowater.velochatx.mapper.migration;

import org.nitrowater.waterapi.domain.infra.io.database.migration.AbstractMigration;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Migration 002: Create player_blocked table.
 * Replaces the old player_ignore and player_reject tables with a single unified table.
 */
public class V002__CreateIgnoreRejectTables extends AbstractMigration {

    public V002__CreateIgnoreRejectTables() {
        super("002", "Create unified player_blocked table");
    }

    @Override
    public void up(Connection conn) throws SQLException {
        // Drop old tables if they exist
        dropTable(conn, "player_ignore");
        dropTable(conn, "player_reject");
        dropTable(conn, "player_ban_list");

        // Create unified player_blocked junction table
        createTable(conn, "player_blocked",
                "owner_uuid TEXT NOT NULL",
                "target_uuid TEXT NOT NULL",
                "PRIMARY KEY (owner_uuid, target_uuid)",
                "FOREIGN KEY (owner_uuid) REFERENCES player(uuid) ON DELETE CASCADE",
                "FOREIGN KEY (target_uuid) REFERENCES player(uuid) ON DELETE CASCADE"
        );

        // Indexes for query performance
        createIndex(conn, "idx_blocked_owner", "player_blocked", "owner_uuid");
        createIndex(conn, "idx_blocked_target", "player_blocked", "target_uuid");
    }
}
