package org.nitrowater.velochatx.testsupport;

import org.nitrowater.velochatx.domain.player.PlayerAttribution;
import org.nitrowater.velochatx.domain.player.PlayerService;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link PlayerService} for event-handler tests.
 * <p>Persistence semantics are mirrored: the first {@link #storeOrUpdatePlayerBaseInfo(UPlayer)}
 * call reports "new" ({@code false}), subsequent calls report "existed" ({@code true}), and
 * attribution mutations are kept in the cache exactly like the real service.</p>
 */
public final class FakePlayerService implements PlayerService {

    private final Map<UUID, PlayerAttribution> attributions = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Set<UUID> existingPlayers = ConcurrentHashMap.newKeySet();

    public final List<UUID> leaveTimeUpdates = new CopyOnWriteArrayList<>();
    public final List<UUID> removedUuids = new CopyOnWriteArrayList<>();
    public final List<UUID> chatOfflineWrites = new CopyOnWriteArrayList<>();

    /** Seed an already-known player name (offline lookup). */
    public FakePlayerService knownPlayer(UUID uuid, String name) {
        names.put(uuid, name);
        existingPlayers.add(uuid);
        return this;
    }

    @Override
    public boolean storeOrUpdatePlayerBaseInfo(UPlayer player) {
        names.put(player.getUuid(), player.getLoginName());
        boolean existed = !existingPlayers.add(player.getUuid());
        getOrCreateAttrs(player.getUuid());
        return existed;
    }

    @Override
    public Optional<PlayerAttribution> getPlayerAttribution(UUID uuid) {
        return Optional.ofNullable(attributions.get(uuid));
    }

    @Override
    public void updateLeaveTime(UUID uuid, long time) {
        leaveTimeUpdates.add(uuid);
    }

    @Override
    public Map<UUID, PlayerAttribution> getOnlinePlayerAttributions() {
        return Map.copyOf(attributions);
    }

    @Override
    public PlayerAttribution getOrCreateAttrs(UUID uuid) {
        return attributions.computeIfAbsent(uuid, ignored -> PlayerAttribution.builder().build());
    }

    @Override
    public void removePlayer(UUID uuid) {
        removedUuids.add(uuid);
        attributions.remove(uuid);
    }

    @Override
    public Optional<String> getPlayerName(UUID uuid) {
        return Optional.ofNullable(names.get(uuid));
    }

    @Override
    public void blockPlayer(UUID uuid, UUID targetUuid) {
        getOrCreateAttrs(uuid).blockPlayer(targetUuid);
    }

    @Override
    public void unblockPlayer(UUID uuid, UUID targetUuid) {
        getOrCreateAttrs(uuid).unblockPlayer(targetUuid);
    }

    @Override
    public void setChatOffline(UUID uuid, boolean offline) {
        getOrCreateAttrs(uuid).setChatOffLine(offline);
        chatOfflineWrites.add(uuid);
    }

    @Override
    public void contributePlaceholders(PlaceholderService service) {
        // Event-handler tests do not need player placeholders.
    }
}
