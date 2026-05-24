package org.juke.framework.session;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeSessionContext} and {@link JukeSessionEntry} getters.
 */
class JukeSessionContextTest {

    @Test
    void defaultContext_allFieldsNull() {
        JukeSessionContext ctx = new JukeSessionContext();
        assertNull(ctx.getSessionId());
        assertNull(ctx.getTrackName());
        assertFalse(ctx.isPlaybackActive());
    }

    @Test
    void setters_roundTrip() {
        JukeSessionContext ctx = new JukeSessionContext();
        ctx.setSessionId("sess-1");
        ctx.setTrackName("track-A");
        ctx.setPlaybackActive(true);

        assertEquals("sess-1", ctx.getSessionId());
        assertEquals("track-A", ctx.getTrackName());
        assertTrue(ctx.isPlaybackActive());
    }

    @Test
    void toString_containsAllFields() {
        JukeSessionContext ctx = new JukeSessionContext();
        ctx.setSessionId("sid");
        ctx.setTrackName("trk");
        ctx.setPlaybackActive(false);
        String s = ctx.toString();
        assertTrue(s.contains("sid"));
        assertTrue(s.contains("trk"));
    }
}

