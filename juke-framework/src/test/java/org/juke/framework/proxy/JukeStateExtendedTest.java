package org.juke.framework.proxy;

import org.juke.framework.runtime.JukeMode;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the missing branches of {@link JukeState}.
 */
class JukeStateExtendedTest {

    private String savedGlobalJuke;

    @BeforeEach
    void setUp() {
        savedGlobalJuke = JukeState.getGlobaljuke();
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobalJuke);
    }

    // ── constants are readable ────────────────────────────────────────────

    @Test
    void constants_haveExpectedValues() {
        assertEquals("juke", JukeState.JUKE);
        assertEquals("record", JukeState.RECORD);
        assertEquals("juke.zip", JukeState.JUKEZIP);
        assertEquals("replay", JukeState.REPLAY);
        assertEquals("ignore", JukeState.IGNORE);
        assertEquals("", JukeState.NONE);
        assertEquals("disable", JukeState.DISABLE);
    }

    // ── getGlobaljuke ─────────────────────────────────────────────────────

    @Test
    void getGlobaljuke_whenNoneMode_returnsNull() {
        JukeState.setGlobaljuke(null);   // null → NONE
        assertNull(JukeState.getGlobaljuke());
    }

    @Test
    void getGlobaljuke_whenReplayMode_returnsReplayString() {
        JukeState.setGlobaljuke(JukeState.REPLAY);
        assertEquals(JukeState.REPLAY, JukeState.getGlobaljuke());
    }

    // ── setGlobaljuke ─────────────────────────────────────────────────────

    @Test
    void setGlobaljuke_toRecord_roundTrips() {
        JukeState.setGlobaljuke(JukeState.RECORD);
        assertEquals(JukeState.RECORD, JukeState.getGlobaljuke());
    }

    // ── isGlobalisable ────────────────────────────────────────────────────

    @Test
    void isGlobalisable_whenDisableMode_returnsTrue() {
        JukeState.setGlobaljuke(JukeState.DISABLE);
        assertTrue(JukeState.isGlobalisable());
    }

    @Test
    void isGlobalisable_whenReplayMode_returnsFalse() {
        JukeState.setGlobaljuke(JukeState.REPLAY);
        assertFalse(JukeState.isGlobalisable());
    }

    // ── setGlobalDisable ──────────────────────────────────────────────────

    @Test
    void setGlobalDisable_true_setsDisableMode() {
        JukeState.setGlobaljuke(JukeState.REPLAY);
        JukeState.setGlobalDisable(true);
        assertTrue(JukeState.isGlobalisable());
    }

    @Test
    void setGlobalDisable_false_whenAlreadyDisable_setsNoneMode() {
        JukeState.setGlobaljuke(JukeState.DISABLE);
        JukeState.setGlobalDisable(false);
        assertFalse(JukeState.isGlobalisable());
        assertNull(JukeState.getGlobaljuke()); // back to NONE
    }

    @Test
    void setGlobalDisable_false_whenNotDisable_preservesCurrentMode() {
        JukeState.setGlobaljuke(JukeState.REPLAY);
        JukeState.setGlobalDisable(false);
        // Mode stays replay (not changed)
        assertEquals(JukeState.REPLAY, JukeState.getGlobaljuke());
    }
}

