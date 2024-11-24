package me.waterwood.velochatx;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class PlayerAttribution {
    private HashSet<UUID> ignorePlayers;
    private HashSet<UUID> rejectPlayers;
    private boolean access;

    public HashSet<UUID> getRejectPlayers() {
        return rejectPlayers;
    }

    public void HashSetRejectPlayers(HashSet<UUID> rejectPlayers) {
        this.rejectPlayers = rejectPlayers;
    }

    public HashSet<UUID> getIgnorePlayers() {
        return ignorePlayers;
    }

    public void HashSetIgnorePlayers(HashSet<UUID> ignorePlayers) {
        this.ignorePlayers = ignorePlayers;
    }

    public boolean isAccess() {
        return access;
    }

    public void setAccess(boolean access) {
        this.access = access;
    }

    public void addIgnorePlayers(UUID name){
        ignorePlayers.add(name);
    }
    public void addRejectPlayers(UUID name){
        ignorePlayers.add(name);
        rejectPlayers.add(name);
    }

    public void removeIJ(UUID name){
        ignorePlayers.remove(name);
        rejectPlayers.remove(name);
    }
    public PlayerAttribution(HashSet<UUID> ignorePlayers,HashSet<UUID> rejectPlayers, boolean access){
        this.access = access;
        this.ignorePlayers = ignorePlayers;
        this.rejectPlayers = rejectPlayers;
    }
}
