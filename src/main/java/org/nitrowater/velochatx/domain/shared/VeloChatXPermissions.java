package org.nitrowater.velochatx.domain.shared;

/**
 * Permission nodes used by VeloChatX.
 * <p>
 * Single source of truth: command registration, the Brigadier command tree and the handlers'
 * internal checks all reference these constants instead of string literals.
 * </p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class VeloChatXPermissions {

    /** Administrative commands (config / reload / status / show-channel-info / placeholder). */
    public static final String ADMIN = "velochatx.admin";

    /** Player-facing commands (on / off / block / unblock / list / mention / vcmsg). */
    public static final String PLAYER = "velochatx.player";

    private VeloChatXPermissions() {
    }
}
