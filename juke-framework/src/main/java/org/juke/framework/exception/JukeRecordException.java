package org.juke.framework.exception;

/**
 * Raised when Juke fails to record an upstream interaction: the recording
 * proxy could not invoke the real service, the response could not be
 * serialised, or the ZIP entry could not be written.
 */
public class JukeRecordException extends JukeException {

    public JukeRecordException(String message) {
        super(message);
    }

    public JukeRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
