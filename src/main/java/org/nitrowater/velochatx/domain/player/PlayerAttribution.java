package org.nitrowater.velochatx.domain.player;

import lombok.Builder;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player attribution data (blocked list, chat offline status).
 *
 * @since 2.1.0
 * @author Danburen
 */
@Getter
@Builder
public class PlayerAttribution {

    @Builder.Default
    private final Set<UUID> blockedPlayers = ConcurrentHashMap.newKeySet();

    @Builder.Default
    // Written by command threads, read on the chat path: volatile for cross-thread visibility.
    private volatile boolean chatOffLine = false;

    /**
     * Block a player.
     */
    public void blockPlayer(UUID uuid) {
        blockedPlayers.add(uuid);
    }

    /**
     * Unblock a player.
     */
    public void unblockPlayer(UUID uuid) {
        blockedPlayers.remove(uuid);
    }

    /**
     * Check if a player is blocked.
     */
    public boolean isBlocked(UUID uuid) {
        return blockedPlayers.contains(uuid);
    }

    public boolean isEmpty() {
        return blockedPlayers.isEmpty();
    }

    public void setChatOffLine(boolean chatOffLine) {
        this.chatOffLine = chatOffLine;
    }

    public boolean isChatOffLine() {
        return chatOffLine;
    }
}
