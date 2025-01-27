package me.waterwood.velochatx.utils;

import me.waterwood.velochatx.VeloChatX;
import org.waterwood.io.DataBase.DatabaseHelper;
import org.waterwood.io.DataBase.SQLite;
import org.waterwood.plugin.WaterPlugin;
import org.waterwood.utils.Colors;
import org.waterwood.utils.JsonStringParser;

import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * An operation class used to process database data
 * @since 1.4.0
 * @author Danburen
 */
public class Mapper extends SQLite {
    private static final String dbFilePath = "database.db";

    public Mapper() {
        super(dbFilePath);
        if(connection == null) {
            VeloChatX.getLogger().warning(WaterPlugin.getMessage("error-connect-database-message").formatted(dbFilePath));
        }else{
            VeloChatX.getLogger().info(Colors.parseColor(WaterPlugin.getMessage("success-connect-database-message")));
            createTable();
        }
    }

    private void createTable() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS players ("
                + "uuid TEXT PRIMARY KEY, "
                + "name TEXT NOT NULL, "
                + "banlist TEXT DEFAULT NULL,"
                + "chatOffline INTEGER DEFAULT 0)";
        try{
            executeCreateSQL(connection, createTableSQL);
            System.out.println("Table successfully created or already exists");
        }catch (SQLException e){
            System.out.println("Error when create database" + e.getMessage());
        }
    }

    public void insertPlayerRecord(UUID uuid, String name) {
        String insertSQL = "INSERT OR REPLACE INTO players (uuid, name) VALUES (?, ?)";
        DatabaseHelper.executeSQL(connection,insertSQL,stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
        },"Error when insert player");
    }

    public void updatePlayerName(UUID uuid, String newName) {
        String updateSQL = "UPDATE players SET name = ? WHERE uuid = ?";
        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setString(1, newName);
            stmt.setString(2, uuid.toString());
        });
    }

    public void updatePlayerBanList(UUID uuid, PlayerAttribution attribution) {
        String updateSQL = "UPDATE players SET banlist = ? WHERE uuid = ?";
        String jsonData = JsonStringParser.hashSetToJson(
                List.of(attribution.getIgnorePlayers(),attribution.getRejectPlayers()),
                "ignoreList","rejectList"
        );
        System.out.println(jsonData);

        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setString(1,jsonData);
            stmt.setString(2, uuid.toString());
        },"Error when update player banlist");
    }

    public String getPlayerName(UUID uuid) {
        String selectSQL = "SELECT name FROM players WHERE uuid = ?";
        final String[] playerName = new String[1];
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {
            stmt.setString(1,uuid.toString());
        },"Error when selecting player's name",rs -> {
            if(rs.next()) {
                playerName[0] = rs.getString("name");
            }else{
                System.out.println("player name not found " + uuid.toString() );
            }
        });
        return playerName[0];
    }

    public Map<String,String> getPlayerNames() {
        String selectSQL = "SELECT * FROM players";
        final Map<String,String> playerInfoMap = new LinkedHashMap<>();
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {}, rs -> {
            while(rs.next()) {
                playerInfoMap.put(rs.getString("name"),rs.getString("uuid"));
            }
        });
        return playerInfoMap;
    }

    public PlayerAttribution getPlayerAttribution(UUID uuid) {
        String selectSQL = "SELECT banlist FROM players WHERE uuid = ?";
        final PlayerAttribution attribution = new PlayerAttribution();
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {},rs -> {
            Map<String,HashSet<String>> rsMap = JsonStringParser
                    .JsonToStringHashMap(rs.getString("banlist"));
            rsMap.get("ignoreList").forEach(stringUUID-> attribution.addIgnorePlayers(UUID.fromString(stringUUID)));
            rsMap.get("rejectList").forEach(stringUUID-> attribution.addRejectPlayers(UUID.fromString(stringUUID)));
            attribution.setAccess(!isPlayerChatOffline(uuid));
        });
       return attribution;
    }

    public boolean isPlayerChatOffline(UUID uuid) {
        String selectSQL = "SELECT charOffline FROM players WHERE uuid = ?";
        final Integer[] offline = new Integer[1];
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {
            stmt.setString(1,uuid.toString());
        }, rs->{
            offline[0] = rs.getInt("charOffline");
        });
        return offline[0] == 1;
    }
}
