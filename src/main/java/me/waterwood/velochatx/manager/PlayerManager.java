package me.waterwood.velochatx.manager;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import me.waterwood.velochatx.methods.Methods;
import me.waterwood.velochatx.utils.Mapper;
import me.waterwood.velochatx.utils.PlayerAttribution;
import me.waterwood.velochatx.utils.SubServer;
import org.waterwood.plugin.velocity.VelocityPlugin;

import java.util.*;
import java.util.jar.Attributes;
import java.util.stream.Collectors;

public class PlayerManager extends Methods {

    private static final Mapper PLAYER_DATA_MAPPER;

    //use static block to make sure Mapper class be initialized once.
    static{
        PLAYER_DATA_MAPPER = new Mapper();
    }

    public static void removePlayer(Player player){
        for(SubServer server : serverInfoMap.values()){
            if(server.getPlayers().contains(player)){
                server.getPlayers().remove(player);
                return;
            }
        }
    }

    public static void storeOrUpdatePlayer(Player player){
        String playerName = PLAYER_DATA_MAPPER.getPlayerName(player.getUniqueId());
        if(playerName == null){
            PLAYER_DATA_MAPPER.insertPlayerRecord(player.getUniqueId(),player.getUsername());
        }else{
            if(! playerName.equals(player.getUsername())){
                PLAYER_DATA_MAPPER.updatePlayerName(player.getUniqueId(),player.getUsername());
            }
        }
    }

    public static void updatePlayerBanList(Player player,PlayerAttribution attribution){
        PLAYER_DATA_MAPPER.updatePlayerBanList(player.getUniqueId(),attribution);
    }

    public static Map<UUID, PlayerAttribution> getOnlinePlayerAttrs(){
        Map<UUID,PlayerAttribution> onlinePlayerAttrs = new HashMap<>();
        proxyServer.getAllPlayers().forEach(player -> {
            onlinePlayerAttrs.put(
                    player.getUniqueId(),PLAYER_DATA_MAPPER.getPlayerAttribution(player.getUniqueId())
            );
        });
        return onlinePlayerAttrs;
    }

    public static String getPlayerName(UUID uuid){
        return PLAYER_DATA_MAPPER.getPlayerName(uuid);
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
