package org.nitrowater.velochatx.domain.channel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards legacy channel-membership detection absorbed from the former
 * {@code BindingRules.legacyMembership} helper (see {@code docs/server-binding-plan.md}).
 */
class ChannelServiceImplMembershipTest {

    @Test
    void legacyMembershipDetectedWhenAnyChannelDeclaresServers() {
        Map<String, Object> legacyMembership = Map.of(
                "login", Map.of("servers", List.of("lobby")),
                "empty", Map.of("display", "x"));
        assertTrue(ChannelServiceImpl.legacyMembership(legacyMembership));

        Map<String, Object> newMembership = Map.of(
                "login", Map.of("display", "x"),
                "empty", Map.of("servers", List.of()));
        assertFalse(ChannelServiceImpl.legacyMembership(newMembership));

        assertFalse(ChannelServiceImpl.legacyMembership(null));
    }
}
