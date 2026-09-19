package org.nitrowater.velochatx.domain.player.infra;

import lombok.Builder;
import lombok.Getter;

/**
 * Entity for player_blocked junction table.
 *
 * @since 2.1.0
 * @author Danburen
 */
@Getter
@Builder
public class PlayerBlocked {

    /** Owner player UUID (who blocked) */
    private final String ownerUuid;

    /** Target player UUID (who is blocked) */
    private final String targetUuid;
}
