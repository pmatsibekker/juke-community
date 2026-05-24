package org.juke.framework.exception;

/**
 * Raised when the metadata describing a proxied type ({@code JukeClass},
 * {@code JukeMethod}, {@code JukeParameterizedType}) is inconsistent or
 * cannot be resolved — usually a class name that no longer exists on the
 * classpath, or a malformed {@code juke.json} file.
 */
public class JukeMetadataException extends JukeException {

    public JukeMetadataException(String message) {
        super(message);
    }

    public JukeMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
