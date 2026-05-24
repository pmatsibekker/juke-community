package org.juke.framework.exception;

/**
 * Raised for any persistence-layer failure: ZIP file I/O, entry not found,
 * deserialization failures, DAO initialization problems.
 */
public class JukeStorageException extends JukeException {

    public JukeStorageException(String message) {
        super(message);
    }

    public JukeStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
