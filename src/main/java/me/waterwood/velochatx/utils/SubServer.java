package me.waterwood.velochatx.utils;

import com.velocitypowered.api.proxy.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Util to temporary store server data
 * Include ServerName,DisplayName,{@link Channel}
 * @since 1.3.3
 */
public class SubServer{
    private String serverName;
    private String serverDisplayName;
    private Channel sourceChannel;
    private final List<Player> players = new ArrayList<>();
    private final Set<Channel> channels = new HashSet<>();

    public SubServer(String serverName, String serverDisplayName) {
        this.serverName = serverName;
        this.serverDisplayName = serverDisplayName;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerDisplayName() {
        return serverDisplayName;
    }

    public void setServerDisplayName(String serverDisplayName) {
        this.serverDisplayName = serverDisplayName;
    }

    public Set<Channel> getChannels() {
        return channels;
    }

    public Channel getSourceChannel() {
        return sourceChannel;
    }

    public void addChannel(Channel channel) {
        if(sourceChannel == null) {
            sourceChannel = channel;
        }
        channels.add(channel);
    }

    public void setSourceChannel(Channel sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players.clear();
        this.players.addAll(players);
    }

    public void addPlayer(Player player) {
        players.add(player);
        channels.forEach(channel -> {
            channel.getPlayers().add(player);
        });
    }

    public void removePlayer(Player player) {
        players.remove(player);
        channels.forEach(channel -> {
            channel.getPlayers().remove(player);
        });
    }
}
