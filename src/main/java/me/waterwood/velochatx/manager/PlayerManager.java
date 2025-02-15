package me.waterwood.velochatx.manager;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.velochatx.mapper.PlayerMapper;
import me.waterwood.velochatx.utils.PlayerAttribution;
import me.waterwood.velochatx.utils.SubServer;
import org.waterwood.plugin.velocity.VelocityPlugin;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerManager extends BasicMethods {

    private static PlayerMapper PLAYER_MAPPER;

    //use static block to make sure mapper class be initialized once.
    public static void initialize() {
        PLAYER_MAPPER = new PlayerMapper();
    }

    public static void removePlayer(Player player){
        for(SubServer server : serverInfoMap.values()){
            if(server.getPlayers().contains(player)){
                server.getPlayers().remove(player);
                return;
            }
        }
    }

    /**
     * Check if player exist in database if not create a new record
     * @param player player to store or update
     * @return is player exist
     */
    public static boolean storeOrUpdatePlayer(Player player){
        String playerName = PLAYER_MAPPER.getPlayerName(player.getUniqueId());
        if(playerName == null){
            PLAYER_MAPPER.insertPlayerRecord(player.getUniqueId(),player.getUsername());
            return true;
        }else{
            if(! playerName.equals(player.getUsername())){
                PLAYER_MAPPER.updatePlayerName(player.getUniqueId(),player.getUsername());
            }
        }
        return false;
    }

    public static void updatePlayerFirstJoinTime(Player player,Long time){
        PLAYER_MAPPER.updatePlayerFirstJoinTime(player.getUniqueId(),new Timestamp(time));
    }

    public static void updatePlayerLeftTime(Player player,Long time){
        PLAYER_MAPPER.updatePlayerLeftTime(player.getUniqueId(),new Timestamp(time));
    }

    public static void updatePlayerAttrs(Player player,PlayerAttribution attribution){
        PLAYER_MAPPER.updatePlayerBanList(player.getUniqueId(),attribution);
        PLAYER_MAPPER.updatePlayerChatOffline(player.getUniqueId(),attribution.isChatOffLine());
    }

    public static Map<UUID, PlayerAttribution> getOnlinePlayerAttrs(){
        Map<UUID,PlayerAttribution> onlinePlayerAttrs = new HashMap<>();
        proxyServer.getAllPlayers().forEach(player -> {
            onlinePlayerAttrs.put(
                    player.getUniqueId(), PLAYER_MAPPER.getPlayerAttribution(player.getUniqueId())
            );
        });
        return onlinePlayerAttrs;
    }

    public static String getPlayerName(UUID uuid){
        return PLAYER_MAPPER.getPlayerName(uuid);
    }

    /**
     * Get player's language code
     * @param sourcePlayer target player
     * @return player local lang code
     */
    public static String getPlayerLangCode(Player sourcePlayer){
        return Optional.ofNullable(sourcePlayer.getEffectiveLocale())
                .map(Locale::getLanguage)
                .orElse("en");
    }

    public static List<String> getAllPlayer(CommandSource source) {
        List<String> players = new ArrayList<>(VelocityPlugin.getAllPlayerName());
        if (source instanceof Player player) {
            players = players.stream()
                    .filter(p -> !p.equals(player.getUsername()))
                    .collect(Collectors.toList());
        }
        return players;
    }
}
