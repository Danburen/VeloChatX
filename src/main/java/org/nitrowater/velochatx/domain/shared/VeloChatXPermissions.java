package org.nitrowater.velochatx.domain.shared;

import org.nitrowater.waterapi.domain.shared.permission.PermissionBuilder;
import org.nitrowater.waterapi.domain.spi.CommandNode;

import java.util.List;

/**
 * Permission nodes used by VeloChatX.
 * <p>
 * Single source of truth: command registration, the Brigadier command tree and the handlers'
 * internal checks all reference these constants instead of string literals. The nodes are
 * built from one {@link PermissionBuilder} tree rooted at {@code velochatx}, so a node
 * string can never drift from its declared tree position.
 * </p>
 *
 * <p>Every subcommand carries its own node ({@code velochatx.player.<cmd>} /
 * {@code velochatx.admin.<cmd>}) accepted <b>any-of</b> together with the group node and the
 * admin group node, so grants stay fine-grained without breaking existing setups that only
 * know {@code velochatx.player} / {@code velochatx.admin}.</p>
 *
 * @since 2.1.0
 * @author Danburen
 */
public final class VeloChatXPermissions {

    /** The permission tree root every VeloChatX node is built from. */
    private static final PermissionBuilder ROOT = PermissionBuilder.base("velochatx");

    /** Administrative commands (config / reload / status / show-channel-info / placeholder). */
    public static final String ADMIN = ROOT.node("admin").build();

    /** Player-facing commands (on / off / block / unblock / list / mention / vcmsg). */
    public static final String PLAYER = ROOT.node("player").build();

    /**
     * Accepted nodes for a player-facing subcommand (any-of):
     * {@code velochatx.player.<command>}, {@code velochatx.player}, {@code velochatx.admin}.
     *
     * @param command the subcommand name (e.g. {@code mention})
     * @return the immutable any-of permission list
     * @since 2.1.0
     */
    public static List<String> playerNodes(String command) {
        return List.of(ROOT.node("player").node(command).build(), PLAYER, ADMIN);
    }

    /**
     * Accepted nodes for an administrative subcommand (any-of):
     * {@code velochatx.admin.<command>}, {@code velochatx.admin}.
     *
     * @param command the subcommand name (e.g. {@code status})
     * @return the immutable any-of permission list
     * @since 2.1.0
     */
    public static List<String> adminNodes(String command) {
        return List.of(ROOT.node("admin").node(command).build(), ADMIN);
    }

    /**
     * Any-of permission nodes for a subcommand from its access tier. Mapping lives here
     * (not WaterAPI) because the fallback nodes are VeloChatX policy; the exhaustive
     * switch makes a new {@link CommandNode.Access} value a compile error at this point.
     *
     * @since 2.1.0
     */
    public static List<String> nodesFor(String command, CommandNode.Access access) {
        return switch (access) {
            case OPEN -> List.of();
            case ADMIN -> adminNodes(command);
            case PLAYER -> playerNodes(command);
        };
    }

    private VeloChatXPermissions() {
    }
}
