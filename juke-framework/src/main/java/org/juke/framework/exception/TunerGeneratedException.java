package org.juke.framework.exception;

/**
 * Deprecated alias for {@link JukeTunerException}. Retained so that
 * existing callers — most importantly the replay handlers that catch this
 * type by name — continue to work. Prefer {@link JukeTunerException} in
 * new code.
 */
@Deprecated
public class TunerGeneratedException extends JukeTunerException {

    public TunerGeneratedException(Exception ex) {
        super(ex);
    }
}
