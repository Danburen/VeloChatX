package me.waterwood.velochatx.mapper;

import me.waterwood.velochatx.utils.PlayerAttribution;
import org.waterwood.io.DataBase.DatabaseHelper;
import org.waterwood.utils.JsonStringParser;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * An operation class used to process database data
 * @since 2.0.0
 * @author Danburen
 */
public class PlayerMapper extends MapperBase {
    public PlayerMapper() {
        super();
    }
    @Override
    protected void createTable() {
        String createTableSQL =
                // player(uuid,name)
                "CREATE TABLE IF NOT EXISTS player ("  +
                    "uuid TEXT PRIMARY KEY, "  +
                    "name TEXT NOT NULL ); "  +
                // player_status(uuid,ban_list,is_chat_offline,first_join_time,left_time)
                "CREATE TABLE IF NOT EXISTS player_status (" +
                    "uuid TEXT PRIMARY KEY," +
                    "ban_list TEXT DEFAULT NULL,"  +
                    "is_chat_offline INTEGER DEFAULT 0," +
                    "first_join_time TIMESTAMP NOT NULL," +
                    "left_time TIMESTAMP DEFAULT NULL," +
                    "FOREIGN KEY (uuid) REFERENCES player(uuid) ON DELETE CASCADE ON UPDATE CASCADE ); ";
                // player_data(uuid,server_name,online_time)...
        try{
            executeCreateSQL(connection, createTableSQL);
        }catch (SQLException e){
            System.out.println("Error when create database" + e.getMessage());
        }
    }

    public void insertPlayerRecord(UUID uuid, String name) {
        String insertPlayerSQL = "MERGE INTO player (uuid, name) VALUES (?, ?)";
        String insertPlayerStatusSQL = "MERGE INTO player_status (uuid,first_join_time) VALUES (?,?)";
        DatabaseHelper.executeSQL(connection,insertPlayerSQL,stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
        },"Error when insert player");
        DatabaseHelper.executeSQL(connection,insertPlayerStatusSQL,stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        },"Error when insert player_status");
    }

    public void updatePlayerName(UUID uuid, String newName) {
        String updateSQL = "UPDATE player SET name = ? WHERE uuid = ?";
        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setString(1, newName);
            stmt.setString(2, uuid.toString());
        });
    }

    public void updatePlayerBanList(UUID uuid, PlayerAttribution attribution) {
        String updateSQL = "UPDATE player_status SET ban_list = ? WHERE uuid = ?";
        String jsonData = JsonStringParser.hashSetToJson(
                List.of(attribution.getIgnorePlayers(),attribution.getRejectPlayers()),
                "ignoreList","rejectList"
        );

        System.out.println(jsonData);

        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setString(1,jsonData);
            stmt.setString(2, uuid.toString());
        },"Error when update player ban_list");
    }

    public void updatePlayerChatOffline(UUID uuid,boolean offline) {
        String updateSQL = "UPDATE player_status SET is_chat_offline = ? WHERE uuid = ?";
        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setInt(1,offline ? 1 : 0);
        });
    }

    public void updatePlayerFirstJoinTime(UUID uuid, Timestamp firstJoinTime) {
        String updateSQL = "UPDATE player_status SET first_join_time = ? WHERE uuid = ?";
        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setTimestamp(1, firstJoinTime);
            stmt.setString(2, uuid.toString());
        });
    }

    public void updatePlayerLeftTime(UUID uuid, Timestamp leftTime) {
        String updateSQL = "UPDATE player_status SET left_time = ? WHERE uuid = ?";
        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setTimestamp(1, leftTime);
            stmt.setString(2, uuid.toString());
        });
    }

    public String getPlayerName(UUID uuid) {
        String selectSQL = "SELECT name FROM player WHERE uuid = ?";
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
        String selectSQL = "SELECT * FROM player";
        final Map<String,String> playerInfoMap = new LinkedHashMap<>();
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {}, rs -> {
            while(rs.next()) {
                playerInfoMap.put(rs.getString("name"),rs.getString("uuid"));
            }
        });
        return playerInfoMap;
    }

    public PlayerAttribution getPlayerAttribution(UUID uuid) {
        String selectSQL = "SELECT ban_list FROM player_status WHERE uuid = ?";
        final PlayerAttribution attribution = new PlayerAttribution();
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {
            stmt.setString(1,uuid.toString());
        },rs -> {
            Map<String,HashSet<String>> rsMap = JsonStringParser
                    .JsonToStringHashMap(rs.getString("ban_list"));
            rsMap.get("ignoreList").forEach(stringUUID-> attribution.addIgnorePlayers(UUID.fromString(stringUUID)));
            rsMap.get("rejectList").forEach(stringUUID-> attribution.addRejectPlayers(UUID.fromString(stringUUID)));
            attribution.setAccess(!isPlayerChatOffline(uuid));
        });
       return attribution;
    }

    public boolean isPlayerChatOffline(UUID uuid) {
        String selectSQL = "SELECT is_chat_offline FROM player_status WHERE uuid = ?";
        final Integer[] offline = new Integer[1];
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {
            stmt.setString(1,uuid.toString());
        }, rs->{
            offline[0] = rs.getInt("is_chat_offline");
        });
        return offline[0] == 1;
    }
}
