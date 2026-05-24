package org.juke.framework.exception;

/**
 * Raised during replay when the requested ZIP entry / sequence index cannot
 * be located — for example when the test invokes a method more times than
 * were recorded, or when a discriminated entry is missing from the track.
 */
public class JukeReplayNotFoundException extends JukeReplayException {

    public JukeReplayNotFoundException(String message) {
        super(message);
    }

    public JukeReplayNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
