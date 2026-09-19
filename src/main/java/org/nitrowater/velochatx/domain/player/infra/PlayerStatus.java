package org.nitrowater.velochatx.domain.player.infra;

import lombok.Builder;
import lombok.Getter;

import java.sql.Timestamp;

/**
 * Player status entity mapped to the {@code player_status} table.
 *
 * @since 2.1.0
 * @author Danburen
 */
@Getter
@Builder
public class PlayerStatus {

    /** Player UUID (PRIMARY KEY, FK → player.uuid) */
    private final String uuid;

    /** Whether the player has chat offline mode enabled */
    private final boolean chatOffLine;

    /** Timestamp of the player's first join */
    private final Timestamp firstJoinTime;

    /** Timestamp of the player's last leave, null if never left */
    private final Timestamp leftTime;
}
