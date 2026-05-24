package org.juke.framework.exception;

/**
 * Legacy unchecked exception for Juke storage failures. Originally extended
 * {@link java.io.IOException}; now extends {@link JukeStorageException}
 * so it participates in the Juke exception hierarchy and no longer
 * requires {@code throws} declarations on every signature.
 * <p>
 * Prefer {@link JukeStorageException} in new code. Existing
 * {@code catch (JukeAccessException)} blocks continue to work unchanged.
 */
public class JukeAccessException extends JukeStorageException {

    public JukeAccessException(String text) {
        super(text);
    }

    public JukeAccessException(String text, Throwable cause) {
        super(text, cause);
    }

    public JukeAccessException() {
        super("Juke access failure");
    }
}
