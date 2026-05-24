package org.juke.framework.session;

import org.juke.framework.exception.JukeAccessException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Registry interface for managing cookie-based playback sessions.
 * <p>
 * This interface allows for easier testing via mocking and supports
 * alternative implementations (e.g., distributed session storage).
 */
public interface SessionRegistry {

    /**
     * Creates a new playback session for the given track.
     *
     * @param trackName the recording name (maps to a ZIP file under the juke path)
     * @return the newly created session entry
     */
    JukeSessionEntry create(String trackName);

    /**
     * Creates a new playback session with an optional human-readable description.
     * Implementations that do not override this default simply delegate to
     * {@link #create(String)}, ignoring the description.
     *
     * @param trackName   the recording name
     * @param description optional test description stored in the session entry
     * @return the newly created session entry
     */
    default JukeSessionEntry create(String trackName, String description) {
        return create(trackName);
    }

    /**
     * Validates that a session exists and matches the given track name.
     *
     * @param sessionId the session UUID
     * @param trackName the expected track name
     * @return {@code true} if the session is valid
     */
    boolean isValid(String sessionId, String trackName);

    /**
     * Retrieves a session entry by ID.
     *
     * @param sessionId the session UUID
     * @return the session entry, or empty if not found
     */
    Optional<JukeSessionEntry> get(String sessionId);

    /**
     * Invalidates (removes) a session.
     *
     * @param sessionId the session UUID to remove
     */
    void invalidate(String sessionId);

    /**
     * Evicts sessions older than the configured TTL.
     */
    void evictExpired();

    /**
     * Returns the number of active sessions (useful for monitoring/testing).
     */
    int size();

    /**
     * Returns a point-in-time snapshot of all active session entries. The
     * returned collection is safe to iterate without holding any internal
     * locks and is independent of subsequent registry mutations.
     */
    Collection<JukeSessionEntry> snapshot();

    /**
     * Returns all completed (invalidated) sessions for the given track, in
     * the order they were stopped. Used by the report endpoint to build the
     * session history section.
     *
     * @param trackName the recording track name
     * @return unmodifiable list of completed entries; empty if none recorded yet
     */
    default List<JukeSessionEntry> getCompletedSessions(String trackName) {
        return List.of();
    }
}

