package org.juke.framework.exception;

/**
 * Deprecated alias for {@link JukeReplayMismatchException}. Retained so that
 * existing callers (including {@link org.juke.framework.storage.JukeHelper})
 * and their catch blocks continue to compile; prefer
 * {@link JukeReplayMismatchException} in new code.
 *
 * @see org.juke.framework.storage.JukeHelper#validateInputArgs
 */
@Deprecated
public class JukeInputMismatchException extends JukeReplayMismatchException {

    public JukeInputMismatchException(String sequencedId, String message) {
        super(sequencedId, message);
    }
}
