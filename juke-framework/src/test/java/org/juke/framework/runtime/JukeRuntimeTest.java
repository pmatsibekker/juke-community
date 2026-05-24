package org.juke.framework.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class JukeRuntimeTest {

    @Test
    void modeFromLegacyStringHandlesNullAndUnknown() {
        assertEquals(JukeMode.NONE, JukeMode.fromLegacyString(null));
        assertEquals(JukeMode.NONE, JukeMode.fromLegacyString("bogus"));
    }

    @Test
    void modeFromLegacyStringIsCaseInsensitive() {
        assertEquals(JukeMode.RECORD, JukeMode.fromLegacyString("RECORD"));
        assertEquals(JukeMode.REPLAY, JukeMode.fromLegacyString("replay"));
        assertEquals(JukeMode.IGNORE, JukeMode.fromLegacyString("Ignore"));
    }

    @Test
    void modeIsActivePredicate() {
        assertTrue(JukeMode.RECORD.isActive());
        assertTrue(JukeMode.REPLAY.isActive());
        assertFalse(JukeMode.IGNORE.isActive());
        assertFalse(JukeMode.NONE.isActive());
        assertFalse(JukeMode.DISABLE.isActive());
    }

    @Test
    void noneSentinelIsPassthrough() {
        assertEquals(JukeMode.NONE, JukeRuntime.NONE.mode());
        assertNull(JukeRuntime.NONE.storage());
        assertFalse(JukeRuntime.NONE.isActive());
    }

    @Test
    void builderProducesImmutableValueObject() {
        ObjectMapper mapper = new ObjectMapper();
        JukeRuntime rt = JukeRuntime.builder()
                .mode(JukeMode.IGNORE)
                .marshaller(mapper)
                .track("/tmp", "test-track")
                .build();

        assertEquals(JukeMode.IGNORE, rt.mode());
        assertSame(mapper, rt.marshaller());
        assertEquals("/tmp", rt.trackPath());
        assertEquals("test-track", rt.trackName());
        // Storage stays null because mode is not active.
        assertNull(rt.storage());
    }

    @Test
    void builderRejectsNullMode() {
        assertThrows(NullPointerException.class,
                () -> JukeRuntime.builder().mode(null));
    }

    @Test
    void holderAlwaysReturnsSensibleRuntime() {
        // After Phase 3 Step E the holder is the single source of truth; it
        // should always expose a non-null runtime with a non-null mode and
        // marshaller, even before any setter has run.
        JukeRuntime current = JukeRuntimeHolder.current();
        assertNotNull(current);
        assertNotNull(current.mode());
        assertNotNull(current.marshaller());
    }

    @Test
    void withModeReturnsCopyWithNewMode() {
        JukeRuntime rt = JukeRuntime.builder().mode(JukeMode.IGNORE).build();
        JukeRuntime rt2 = rt.withMode(JukeMode.NONE);
        assertEquals(JukeMode.NONE, rt2.mode());
        // original unchanged
        assertEquals(JukeMode.IGNORE, rt.mode());
    }

    @Test
    void withStorageReturnsCopyWithNewStorage() {
        JukeRuntime rt = JukeRuntime.builder().mode(JukeMode.IGNORE).build();
        JukeRuntime rt2 = rt.withStorage(null);
        assertNull(rt2.storage());
    }

    @Test
    void cachesMutableAndSharedAcrossCopies() {
        JukeRuntime rt = JukeRuntime.builder().mode(JukeMode.IGNORE).build();
        JukeRuntime copy = rt.withMode(JukeMode.NONE);
        // Same cache references preserved
        assertSame(rt.proxyCache(), copy.proxyCache());
        assertSame(rt.replayHandlerCache(), copy.replayHandlerCache());
        assertSame(rt.metadataCache(), copy.metadataCache());
        assertSame(rt.tunerParticipants(), copy.tunerParticipants());
    }

    @Test
    void modeFromLegacyString_record() {
        assertEquals(JukeMode.RECORD, JukeMode.fromLegacyString("record"));
    }

    @Test
    void modeFromLegacyString_disable() {
        assertEquals(JukeMode.DISABLE, JukeMode.fromLegacyString("disable"));
    }

    @Test
    void modeIsRecord() {
        assertTrue(JukeMode.RECORD.isRecord());
        assertFalse(JukeMode.REPLAY.isRecord());
    }

    @Test
    void modeIsReplay() {
        assertTrue(JukeMode.REPLAY.isReplay());
        assertFalse(JukeMode.RECORD.isReplay());
    }

    @Test
    void legacyString_matchesJukeStateConstants() {
        assertEquals("record", JukeMode.RECORD.legacyString());
        assertEquals("replay", JukeMode.REPLAY.legacyString());
        assertEquals("ignore", JukeMode.IGNORE.legacyString());
        assertEquals("none", JukeMode.NONE.legacyString());
    }
}
