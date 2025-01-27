package me.waterwood.velochatx.utils;

import com.velocitypowered.api.proxy.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A class to holder server's message communicate
 * <p></p>
 * a server can be in several channels
 * a channel can contain several {@link SubServer}
 * @since 1.4.0
 * @author Danburen
 */
public class Channel {
    private String channelName;
    private String channelDisplayName;
    private final Set<SubServer> servers;
    private final List<Player> players = new ArrayList<>();
    public Channel(String channelName, String channelDisplayName, Set<SubServer> servers) {
        this.channelName = channelName;
        this.channelDisplayName = channelDisplayName;
        this.servers = servers;
    }

    public void addServer(SubServer server) {
        servers.add(server);
    }

    public int getServerCount() {
        return servers.size();
    }

    public String getChannelName() {
        return channelName;
    }

    public String getChannelDisplayName() {
        return channelDisplayName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Set<SubServer> getServers() {
        return servers;
    }

    public void setChannelDisplayName(String channelDisplayName) {
        this.channelDisplayName = channelDisplayName;
    }

    public List<Player> getPlayers() {
        return players;
    }
}
