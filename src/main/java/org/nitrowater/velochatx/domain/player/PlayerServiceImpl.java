package org.nitrowater.velochatx.domain.player;

import org.nitrowater.velochatx.domain.player.infra.PlayerBlockedMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerMapper;
import org.nitrowater.velochatx.domain.player.infra.PlayerStatusMapper;
import org.nitrowater.waterapi.domain.model.UPlayer;
import org.nitrowater.waterapi.domain.placeholder.PlaceholderService;
import org.nitrowater.waterapi.domain.placeholder.PlaceholdersContributor;
import org.nitrowater.waterapi.domain.spi.PermissionService;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link PlayerService} and {@link PlaceholdersContributor}.
 */
public class PlayerServiceImpl implements PlayerService, PlaceholdersContributor {

    private final PermissionService permissionService;
    private final PlayerMapper playerMapper;
    private final PlayerStatusMapper statusMapper;
    private final PlayerBlockedMapper blockedMapper;

    private final Map<UUID, PlayerAttribution> attributions = new ConcurrentHashMap<>();

    public PlayerServiceImpl(PermissionService permissionService,
                            PlayerMapper playerMapper,
                            PlayerStatusMapper statusMapper,
                            PlayerBlockedMapper blockedMapper) {
        this.permissionService = permissionService;
        this.playerMapper = playerMapper;
        this.statusMapper = statusMapper;
        this.blockedMapper = blockedMapper;
    }

    @Override
    public boolean storeOrUpdatePlayerBaseInfo(UPlayer player) {
        UUID uuid = player.getUuid();

        // Ensure the attribution is cached (loads from DB on first sight).
        getOrCreateAttrs(uuid);

        String playerName = playerMapper.getPlayerName(uuid);
        if (playerName == null) {
            playerMapper.insertPlayerRecord(uuid, player.getLoginName());
            return false;
        } else {
            if (!playerName.equals(player.getLoginName())) {
                playerMapper.updatePlayerName(uuid, player.getLoginName());
            }
            return true;
        }
    }

    @Override
    public Optional<PlayerAttribution> getPlayerAttribution(UUID uuid) {
        return Optional.ofNullable(attributions.get(uuid));
    }

    @Override
    public void updateLeaveTime(UUID uuid, long time) {
        statusMapper.updateLeftTime(uuid, new java.sql.Timestamp(time));
    }

    @Override
    public Map<UUID, PlayerAttribution> getOnlinePlayerAttributions() {
        return Map.copyOf(attributions);
    }

    @Override
    public PlayerAttribution getOrCreateAttrs(UUID uuid) {
        return this.attributions.computeIfAbsent(uuid, this::loadAttributionFromDb);
    }

    @Override
    public void removePlayer(UUID uuid) {
        this.attributions.remove(uuid);
    }

    @Override
    public void blockPlayer(UUID uuid, UUID targetUuid) {
        PlayerAttribution attrs = getOrCreateAttrs(uuid);
        attrs.blockPlayer(targetUuid);
        blockedMapper.addBlock(uuid, targetUuid);
    }

    @Override
    public void unblockPlayer(UUID uuid, UUID targetUuid) {
        PlayerAttribution attrs = getOrCreateAttrs(uuid);
        attrs.unblockPlayer(targetUuid);
        blockedMapper.removeBlock(uuid, targetUuid);
    }

    @Override
    public void setChatOffline(UUID uuid, boolean offline) {
        PlayerAttribution attrs = getOrCreateAttrs(uuid);
        attrs.setChatOffLine(offline);
        statusMapper.updateChatOffline(uuid, offline);
    }

    @Override
    public void contributePlaceholders(PlaceholderService service) {
        service.register("player", ctx -> ctx.getPlayer()
                .map(UPlayer::getLoginName).orElse(""));
        service.register("ping", ctx -> ctx.getPlayer()
                .map(p -> String.valueOf(p.getPing())).orElse("0"));
        service.register("prefix", ctx -> ctx.getPlayer()
                .flatMap(permissionService::getPrefix).orElse(""));
        service.register("suffix", ctx -> ctx.getPlayer()
                .flatMap(permissionService::getSuffix).orElse(""));
        service.register("group", ctx -> ctx.getPlayer()
                .flatMap(permissionService::getGroup).orElse(""));
    }

    // ======================== Private methods ========================

    private PlayerAttribution loadAttributionFromDb(UUID uuid) {
        Set<UUID> blockedList = blockedMapper.getBlockedList(uuid);
        boolean chatOffLine = statusMapper.isChatOffline(uuid);

        // NOTE: must NOT mutate the cache here - this runs inside computeIfAbsent.
        PlayerAttribution attribution = PlayerAttribution.builder()
                .chatOffLine(chatOffLine)
                .build();
        attribution.getBlockedPlayers().addAll(blockedList);
        return attribution;
    }
}
