package org.nitrowater.velochatx.domain.player.infra;

import lombok.Builder;
import lombok.Getter;

/**
 * Player entity mapped to the {@code player} table.
 *
 * @since 2.1.0
 * @author Danburen
 */
@Getter
@Builder
public class Player {

    /** Player UUID as string (PRIMARY KEY) */
    private final String uuid;

    /** Player display name */
    private final String name;

    /** Soft-delete flag */
    private final boolean deleted;

    /** Auto-increment index */
    private final int index;
}
