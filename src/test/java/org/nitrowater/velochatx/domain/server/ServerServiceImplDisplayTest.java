package org.nitrowater.velochatx.domain.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the unbound-default display policy (auto {@code []} when the display feature is on)
 * and the bracket-decoration detector used by {@link ServerServiceImpl#unboundDefaultDisplay}.
 * See {@code docs/server-binding-plan.md}.
 */
class ServerServiceImplDisplayTest {

    @Test
    void unboundDefaultWrapsRawNameWhenDisplayEnabled() {
        assertEquals("[skyblock]", ServerServiceImpl.unboundDefaultDisplay("skyblock", true));
    }

    @Test
    void unboundDefaultKeepsRawNameWhenDisplayDisabled() {
        assertEquals("skyblock", ServerServiceImpl.unboundDefaultDisplay("skyblock", false));
    }

    @Test
    void unboundDefaultDoesNotDoubleWrap() {
        assertEquals("[lobby]", ServerServiceImpl.unboundDefaultDisplay("[lobby]", true));
        assertEquals("【大厅】", ServerServiceImpl.unboundDefaultDisplay("【大厅】", true));
    }

    @Test
    void unboundDefaultPassesThroughNullOrEmpty() {
        assertEquals(null, ServerServiceImpl.unboundDefaultDisplay(null, true));
        assertEquals("", ServerServiceImpl.unboundDefaultDisplay("", true));
    }

    @Test
    void hasBracketDecorationDetectsPairsOnly() {
        assertTrue(ServerServiceImpl.hasBracketDecoration("[x]"));
        assertTrue(ServerServiceImpl.hasBracketDecoration("【x】"));
        assertFalse(ServerServiceImpl.hasBracketDecoration("x"));
        assertFalse(ServerServiceImpl.hasBracketDecoration("[x"));
        assertFalse(ServerServiceImpl.hasBracketDecoration("x]"));
        assertFalse(ServerServiceImpl.hasBracketDecoration(""));
        assertFalse(ServerServiceImpl.hasBracketDecoration(null));
    }
}
