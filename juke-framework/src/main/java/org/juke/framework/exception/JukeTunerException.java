package org.juke.framework.exception;

/**
 * Raised when a Juke tuner task wants to propagate a user-defined exception
 * back to the caller of the proxied method. The original exception is carried
 * in {@link #getWrappedException()} so that replay code can rethrow it with
 * its original type intact.
 * <p>
 * Unchecked successor of the legacy {@link TunerGeneratedException}, which
 * remains in place as an alias for binary compatibility.
 */
public class JukeTunerException extends JukeException {

    private final Exception wrappedException;

    public JukeTunerException(Exception wrapped) {
        super(wrapped != null ? wrapped.getClass().getName() + ": " + wrapped.getMessage()
                              : "tuner-generated exception", wrapped);
        this.wrappedException = wrapped;
    }

    public JukeTunerException(String message, Exception wrapped) {
        super(message, wrapped);
        this.wrappedException = wrapped;
    }

    public Exception getWrappedException() {
        return wrappedException;
    }
}
