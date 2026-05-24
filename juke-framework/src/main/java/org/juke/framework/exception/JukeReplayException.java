package org.juke.framework.exception;

/**
 * Raised during replay when a recorded interaction cannot be satisfied.
 * See subtypes {@link JukeReplayMismatchException} (args do not match what
 * was recorded) and {@link JukeReplayNotFoundException} (no entry exists
 * for the requested method / sequence).
 */
public class JukeReplayException extends JukeException {

    public JukeReplayException(String message) {
        super(message);
    }

    public JukeReplayException(String message, Throwable cause) {
        super(message, cause);
    }
}
