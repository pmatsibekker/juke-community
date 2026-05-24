package org.juke.framework.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the exception classes not yet tested: JukeAccessException,
 * JukeStorageException, JukeTunerException, TunerGeneratedException.
 */
class ExceptionExtendedTest {

    // ── JukeStorageException ──────────────────────────────────────────────

    @Test
    void jukeStorageException_messageConstructor() {
        JukeStorageException ex = new JukeStorageException("storage failed");
        assertEquals("storage failed", ex.getMessage());
        assertTrue(ex instanceof JukeException);
    }

    @Test
    void jukeStorageException_causeConstructor() {
        Throwable cause = new RuntimeException("io");
        JukeStorageException ex = new JukeStorageException("storage + cause", cause);
        assertEquals("storage + cause", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    // ── JukeAccessException ───────────────────────────────────────────────

    @Test
    void jukeAccessException_messageConstructor() {
        JukeAccessException ex = new JukeAccessException("access error");
        assertEquals("access error", ex.getMessage());
        assertTrue(ex instanceof JukeStorageException);
    }

    @Test
    void jukeAccessException_causeConstructor() {
        Throwable cause = new RuntimeException("root");
        JukeAccessException ex = new JukeAccessException("access + cause", cause);
        assertEquals("access + cause", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void jukeAccessException_noArgConstructor_hasDefaultMessage() {
        JukeAccessException ex = new JukeAccessException();
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("Juke"));
    }

    // ── JukeTunerException ────────────────────────────────────────────────

    @Test
    void jukeTunerException_wrappedConstructor_withException() {
        Exception inner = new IllegalStateException("tuner error");
        JukeTunerException ex = new JukeTunerException(inner);
        assertSame(inner, ex.getWrappedException());
        assertTrue(ex.getMessage().contains("IllegalStateException"));
        assertTrue(ex.getMessage().contains("tuner error"));
    }

    @Test
    void jukeTunerException_wrappedConstructor_withNull() {
        JukeTunerException ex = new JukeTunerException((Exception) null);
        assertNull(ex.getWrappedException());
        assertTrue(ex.getMessage().contains("tuner-generated exception"));
    }

    @Test
    void jukeTunerException_messageAndWrappedConstructor() {
        Exception inner = new RuntimeException("cause");
        JukeTunerException ex = new JukeTunerException("custom message", inner);
        assertEquals("custom message", ex.getMessage());
        assertSame(inner, ex.getWrappedException());
    }

    // ── TunerGeneratedException ───────────────────────────────────────────

    @Test
    @SuppressWarnings("deprecation")
    void tunerGeneratedException_isDeprecatedAlias() {
        Exception inner = new RuntimeException("old");
        TunerGeneratedException ex = new TunerGeneratedException(inner);
        assertSame(inner, ex.getWrappedException());
        assertTrue(ex instanceof JukeTunerException);
    }

    // ── JukeTunerException is-a JukeException hierarchy ──────────────────

    @Test
    void jukeTunerException_isJukeException() {
        JukeTunerException ex = new JukeTunerException(new RuntimeException("x"));
        assertTrue(ex instanceof JukeException);
        assertTrue(ex instanceof RuntimeException);
    }
}

