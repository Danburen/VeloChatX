package me.waterwood.velochatx.mapper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.waterwood.velochatx.utils.PlayerAttribution;
import org.waterwood.io.database.DatabaseHelper;
import org.waterwood.utils.JsonStringParser;

import javax.annotation.Nullable;
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
        String insertPlayerStatusSQL = "MERGE INTO player_status (uuid,first_join_time) VALUES (?, ?)";
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
        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setString(1,jsonData);
            stmt.setString(2, uuid.toString());
        },"Error when update player ban_list");
    }

    public void updatePlayerChatOffline(UUID uuid,boolean offline) {
        String updateSQL = "UPDATE player_status SET is_chat_offline = ? WHERE uuid = ?";
        DatabaseHelper.executeSQL(connection,updateSQL,stmt -> {
            stmt.setInt(1,offline ? 1 : 0);
            stmt.setString(2, uuid.toString());
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

    /**
     * Get the player name from player table
     * if not exist return null
     * @param uuid uuid of player
     * @return player's name
     */
    public @Nullable String getPlayerName(UUID uuid) {
        String selectSQL = "SELECT name FROM player WHERE uuid = ?";
        final String[] playerName = new String[1];
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {
            stmt.setString(1,uuid.toString());
        },"Error when selecting player's name",rs -> {
            if(rs.next()) {
                playerName[0] = rs.getString("name");
            }
        });
        return playerName[0];
    }

    /**
     * Return the map of player's info which made up of UUID and Name
     * @return player's info map
     */
    public Map<String,String> getPlayerMap() {
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
            if(rs.next()) {
                String banList = rs.getString("ban_list");
                if(banList != null) {
                    JsonObject jsonObject = JsonParser.parseString(banList).getAsJsonObject();
                    jsonObject.getAsJsonArray("ignoreList").forEach(item->{
                        attribution.addIgnorePlayers(UUID.fromString(item.getAsString()));
                    });
                    jsonObject.getAsJsonArray("rejectList").forEach(item->{
                        attribution.addRejectPlayers(UUID.fromString(item.getAsString()));
                    });
                }else{
                    System.out.println("Player" + uuid + "'s Ban List is empty");
                }
                attribution.setChatOffLine(getPlayerChatOffline(uuid));
            }else{
                System.out.println("No data found for UUID: " + uuid.toString());
            }

        });
       return attribution;
    }

    public boolean getPlayerChatOffline(UUID uuid) {
        String selectSQL = "SELECT is_chat_offline FROM player_status WHERE uuid = ?";
        final Integer[] offline = new Integer[1];
        DatabaseHelper.executeSQL(connection,selectSQL,stmt -> {
            stmt.setString(1,uuid.toString());
        }, rs->{
            if(rs.next()){
                int chatOffLine = rs.getInt("is_chat_offline");
                offline[0] = chatOffLine;
            }
        });
        return offline[0] == 1;
    }

    public void showPlayerStatus(UUID uuid) {
        String selectSQL = "SELECT * FROM player_status WHERE uuid = ?";
        DatabaseHelper.executeSQL(connection, selectSQL, stmt -> {
            stmt.setString(1,uuid.toString());
        }, rs -> {
            if(rs.next()){
                System.out.println("UUID: " + rs.getString("uuid"));
                System.out.println("First Join Time: " + rs.getString("first_join_time"));
                System.out.println("Left Time: " + rs.getString("left_time"));
                System.out.println("Is Chat Offline: " + rs.getString("is_chat_offline"));
                System.out.println("Ban List: " + rs.getString("ban_list"));
            }
        });
    }
}
