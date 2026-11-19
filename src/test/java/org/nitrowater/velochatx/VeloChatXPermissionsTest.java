package org.nitrowater.velochatx;

import org.junit.jupiter.api.Test;
import org.nitrowater.velochatx.domain.shared.VeloChatXPermissions;
import org.nitrowater.waterapi.domain.spi.CommandNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden test: locks the exact permission node strings VeloChatX hands to the command
 * tree / registrars.
 * <p>
 * These strings are what admins grant in LuckPerms — an accidental change silently breaks
 * live authorizations, so the permission migration to {@code PermissionBuilder} must keep
 * this output byte-identical.
 * </p>
 *
 * @since 2.1.0
 */
class VeloChatXPermissionsTest {

    @Test
    void groupNodesAreStable() {
        assertEquals("velochatx.admin", VeloChatXPermissions.ADMIN);
        assertEquals("velochatx.player", VeloChatXPermissions.PLAYER);
    }

    @Test
    void adminNodesAcceptOwnCommandThenGroup() {
        for (String command : List.of("config", "reload", "show-channel-info",
                "status", "placeholder")) {
            assertEquals(
                    List.of("velochatx.admin." + command, "velochatx.admin"),
                    VeloChatXPermissions.adminNodes(command),
                    "adminNodes(" + command + ")");
        }
    }

    @Test
    void playerNodesAcceptOwnCommandThenPlayerThenAdmin() {
        for (String command : List.of("on", "off", "block", "unblock", "list",
                "mention", "vcmsg")) {
            assertEquals(
                    List.of("velochatx.player." + command, "velochatx.player", "velochatx.admin"),
                    VeloChatXPermissions.playerNodes(command),
                    "playerNodes(" + command + ")");
        }
    }

    @Test
    void helperOutputMatchesDirectConstruction() {
        // The two helper families are the ONLY sources feeding CommandNode permissions;
        // spot-check that a fresh string-built expectation and the helpers agree for an
        // arbitrary subcommand name (guards against future hardcoded lists drifting).
        assertEquals(List.of("velochatx.admin.custom", "velochatx.admin"),
                VeloChatXPermissions.adminNodes("custom"));
        assertEquals(List.of("velochatx.player.custom", "velochatx.player", "velochatx.admin"),
                VeloChatXPermissions.playerNodes("custom"));
    }

    @Test
    void nodesForDerivesFromAccessTier() {
        // OPEN is unrestricted; ADMIN/PLAYER delegate to the golden-tested families above.
        assertEquals(List.of(),
                VeloChatXPermissions.nodesFor("help", CommandNode.Access.OPEN));
        assertEquals(
                VeloChatXPermissions.adminNodes("status"),
                VeloChatXPermissions.nodesFor("status", CommandNode.Access.ADMIN));
        assertEquals(
                VeloChatXPermissions.playerNodes("on"),
                VeloChatXPermissions.nodesFor("on", CommandNode.Access.PLAYER));
    }
}
