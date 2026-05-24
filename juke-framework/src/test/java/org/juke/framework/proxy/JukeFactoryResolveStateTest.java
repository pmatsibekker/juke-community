package org.juke.framework.proxy;

import org.juke.framework.support.ISampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeFactory#resolveJukeState(String)} branches.
 */
class JukeFactoryResolveStateTest {

    private String savedGlobal;

    @BeforeEach
    void backup() {
        savedGlobal = JukeState.getGlobaljuke();
    }

    @AfterEach
    void restore() {
        JukeState.setGlobaljuke(savedGlobal);
    }

    @Test
    void resolveJukeState_nullInput_returnsNull() {
        assertNull(JukeFactory.resolveJukeState(null));
    }

    @Test
    void resolveJukeState_globalRecord_returnsRecord() {
        JukeState.setGlobaljuke(JukeState.RECORD);
        // non-empty local value → global record wins
        assertEquals(JukeState.RECORD, JukeFactory.resolveJukeState("juke"));
    }

    @Test
    void resolveJukeState_globalReplay_returnsReplay() {
        JukeState.setGlobaljuke(JukeState.REPLAY);
        assertEquals(JukeState.REPLAY, JukeFactory.resolveJukeState("juke"));
    }

    @Test
    void resolveJukeState_globalReplay_localNone_returnsNone() {
        JukeState.setGlobaljuke(JukeState.REPLAY);
        // NONE local value → not overridden by global
        String result = JukeFactory.resolveJukeState(JukeState.NONE);
        // Local is "", so the condition (NONE.equalsIgnoreCase(juke)) is true → falls to else
        assertEquals(JukeState.NONE, result);
    }

    @Test
    void resolveJukeState_globalIgnore_returnsNone() {
        JukeState.setGlobaljuke(JukeState.IGNORE);
        assertEquals(JukeState.NONE, JukeFactory.resolveJukeState("juke"));
    }

    @Test
    void resolveJukeState_noGlobal_returnsLocalValue() {
        JukeState.setGlobaljuke(null); // → NONE mode, legacyString = ""
        assertEquals("juke", JukeFactory.resolveJukeState("juke"));
    }

    @Test
    void resolveJukeState_noGlobal_jukeInput_returnsJukeInput() {
        JukeState.setGlobaljuke(null);
        assertEquals("record", JukeFactory.resolveJukeState("record"));
    }
}

