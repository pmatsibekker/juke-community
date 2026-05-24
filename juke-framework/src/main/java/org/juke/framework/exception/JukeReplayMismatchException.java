package org.juke.framework.exception;

/**
 * Raised during strict-mode replay when the current method arguments do not
 * match the arguments that were recorded for the same entry.
 * <p>
 * Renamed successor of the legacy {@link JukeInputMismatchException}, which
 * remains in place as an alias for binary compatibility.
 */
public class JukeReplayMismatchException extends JukeReplayException {

    private final String sequencedId;

    public JukeReplayMismatchException(String sequencedId, String message) {
        super("Input mismatch for [" + sequencedId + "]: " + message);
        this.sequencedId = sequencedId;
    }

    public String getSequencedId() {
        return sequencedId;
    }
}
