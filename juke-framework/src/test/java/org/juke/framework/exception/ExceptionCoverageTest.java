package org.juke.framework.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ensures all exception classes (previously 0% covered) have at least
 * their constructors and key methods exercised.
 */
class ExceptionCoverageTest {

    // ── JukeReplayException ────────────────────────────────────────────────

    @Test
    void jukeReplayException_messageConstructor() {
        JukeReplayException ex = new JukeReplayException("replay failed");
        assertEquals("replay failed", ex.getMessage());
        assertTrue(ex instanceof JukeException);
    }

    @Test
    void jukeReplayException_causeConstructor() {
        Throwable cause = new RuntimeException("root");
        JukeReplayException ex = new JukeReplayException("with cause", cause);
        assertEquals("with cause", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ── JukeConfigurationException ────────────────────────────────────────

    @Test
    void jukeConfigurationException_messageConstructor() {
        JukeConfigurationException ex = new JukeConfigurationException("bad config");
        assertEquals("bad config", ex.getMessage());
        assertTrue(ex instanceof JukeException);
    }

    @Test
    void jukeConfigurationException_causeConstructor() {
        Throwable cause = new IllegalArgumentException("arg");
        JukeConfigurationException ex = new JukeConfigurationException("config + cause", cause);
        assertEquals("config + cause", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ── JukeReplayMismatchException ───────────────────────────────────────

    @Test
    void jukeReplayMismatchException_messageContainsSequencedId() {
        JukeReplayMismatchException ex = new JukeReplayMismatchException("sig-123", "args differ");
        assertEquals("sig-123", ex.getSequencedId());
        assertTrue(ex.getMessage().contains("sig-123"));
        assertTrue(ex.getMessage().contains("args differ"));
        assertTrue(ex instanceof JukeReplayException);
    }

    // ── BadJukeConfigurationException ────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation")
    void badJukeConfigurationException_isDeprecatedAlias() {
        BadJukeConfigurationException ex = new BadJukeConfigurationException("old API");
        assertEquals("old API", ex.getMessage());
        assertTrue(ex instanceof JukeConfigurationException);
    }

    // ── JukeInputMismatchException ────────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation")
    void jukeInputMismatchException_isDeprecatedAlias() {
        JukeInputMismatchException ex = new JukeInputMismatchException("sig-old", "mismatch");
        assertEquals("sig-old", ex.getSequencedId());
        assertTrue(ex instanceof JukeReplayMismatchException);
    }

    // ── JukeReplayNotFoundException ───────────────────────────────────────

    @Test
    void jukeReplayNotFoundException_messageConstructor() {
        JukeReplayNotFoundException ex = new JukeReplayNotFoundException("not found");
        assertEquals("not found", ex.getMessage());
        assertTrue(ex instanceof JukeReplayException);
    }

    @Test
    void jukeReplayNotFoundException_causeConstructor() {
        Throwable cause = new RuntimeException("io");
        JukeReplayNotFoundException ex = new JukeReplayNotFoundException("missing entry", cause);
        assertEquals("missing entry", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ── JukeRecordException ───────────────────────────────────────────────

    @Test
    void jukeRecordException_messageConstructor() {
        JukeRecordException ex = new JukeRecordException("record error");
        assertEquals("record error", ex.getMessage());
        assertTrue(ex instanceof JukeException);
    }

    @Test
    void jukeRecordException_causeConstructor() {
        Throwable cause = new RuntimeException("io");
        JukeRecordException ex = new JukeRecordException("record + cause", cause);
        assertEquals("record + cause", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ── JukeMetadataException ─────────────────────────────────────────────

    @Test
    void jukeMetadataException_messageConstructor() {
        JukeMetadataException ex = new JukeMetadataException("metadata error");
        assertEquals("metadata error", ex.getMessage());
        assertTrue(ex instanceof JukeException);
    }

    @Test
    void jukeMetadataException_causeConstructor() {
        Throwable cause = new ClassNotFoundException("foo");
        JukeMetadataException ex = new JukeMetadataException("metadata + cause", cause);
        assertEquals("metadata + cause", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}

