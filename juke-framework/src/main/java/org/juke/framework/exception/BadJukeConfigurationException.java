package org.juke.framework.exception;

/**
 * Deprecated alias for {@link JukeConfigurationException}. Retained so that
 * existing callers and catch blocks continue to compile and link; prefer
 * throwing and catching {@link JukeConfigurationException} in new code.
 */
@Deprecated
public class BadJukeConfigurationException extends JukeConfigurationException {

    public BadJukeConfigurationException(String arg) {
        super(arg);
    }
}
