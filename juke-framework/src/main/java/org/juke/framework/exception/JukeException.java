package org.juke.framework.exception;

/**
 * Abstract root of the Juke exception hierarchy.
 * <p>
 * All Juke-specific failures extend this type. It is unchecked so that
 * framework internals do not need to pollute their signatures with
 * {@code throws} declarations — callers catch the most specific subtype
 * they care about, or let the exception propagate.
 * <p>
 * Subtypes follow a domain-oriented naming convention:
 * <ul>
 *   <li>{@link JukeConfigurationException} — bad yaml, missing track, invalid annotation</li>
 *   <li>{@link JukeStorageException} — ZIP I/O, serialization, DAO failures</li>
 *   <li>{@link JukeReplayException} — replay-time failures
 *       (including {@link JukeReplayMismatchException} and
 *        {@link JukeReplayNotFoundException})</li>
 *   <li>{@link JukeRecordException} — record-time failures</li>
 *   <li>{@link JukeMetadataException} — bad {@code JukeClass} / {@code JukeMethod} metadata</li>
 *   <li>{@link JukeTunerException} — wraps a user-defined exception produced by a tuner</li>
 * </ul>
 */
public abstract class JukeException extends RuntimeException {

    protected JukeException(String message) {
        super(message);
    }

    protected JukeException(String message, Throwable cause) {
        super(message, cause);
    }
}
