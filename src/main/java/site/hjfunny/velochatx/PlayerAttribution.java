package site.hjfunny.velochatx;

import java.util.HashSet;
import java.util.List;

public class PlayerAttribution {
    private HashSet<String> ignorePlayers;
    private HashSet<String> rejectPlayers;
    private boolean access;

    public HashSet<String> getRejectPlayers() {
        return rejectPlayers;
    }

    public void HashSetRejectPlayers(HashSet<String> rejectPlayers) {
        this.rejectPlayers = rejectPlayers;
    }

    public HashSet<String> getIgnorePlayers() {
        return ignorePlayers;
    }

    public void HashSetIgnorePlayers(HashSet<String> ignorePlayers) {
        this.ignorePlayers = ignorePlayers;
    }

    public boolean isAccess() {
        return access;
    }

    public void setAccess(boolean access) {
        this.access = access;
    }

    public void addIgnorePlayers(String name){
        ignorePlayers.add(name);
    }
    public void addRejectPlayers(String name){
        ignorePlayers.add(name);
        rejectPlayers.add(name);
    }

    public void removeIJ(String name){
        ignorePlayers.remove(name);
        rejectPlayers.remove(name);
    }
    public PlayerAttribution(HashSet<String> ignorePlayers,HashSet<String> rejectPlayers, boolean access){
        this.access = access;
        this.ignorePlayers = ignorePlayers;
        this.rejectPlayers = rejectPlayers;
    }
}
