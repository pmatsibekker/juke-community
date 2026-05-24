package org.juke.framework.exception;

/**
 * Raised when Juke configuration is invalid: missing properties, malformed
 * yaml, an unknown track name, or a {@code @Juke} annotation applied to an
 * incompatible target.
 */
public class JukeConfigurationException extends JukeException {

    public JukeConfigurationException(String message) {
        super(message);
    }

    public JukeConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
