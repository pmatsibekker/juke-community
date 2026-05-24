package org.juke.framework.session;

import org.juke.framework.config.ConfigUtil;
import org.juke.framework.storage.JukeStorage;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.exception.JukeAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory registry of active cookie-based playback sessions.
 * <p>
 * Keyed by session UUID, each entry holds a reference to the pre-built
 * per-session {@link JukeStorage} DAO and per-interface
 * {@link org.juke.framework.metadata.DataProgramSchedule} instances.
 * <p>
 * This is a singleton Spring bean registered by
 * {@link org.juke.framework.config.JukeConfiguration}.
 */
public class JukeSessionRegistry implements SessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(JukeSessionRegistry.class);

    /** Default session TTL: 1 hour. */
    private static final Duration SESSION_TTL = Duration.ofHours(1);

    private final ConcurrentHashMap<String, JukeSessionEntry> sessions = new ConcurrentHashMap<>();

    /**
     * Completed (invalidated) sessions keyed by track name, in chronological order.
     * Used by {@link #getCompletedSessions(String)} to power the report endpoint.
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<JukeSessionEntry>> completedByTrack =
            new ConcurrentHashMap<>();

    /**
     * Creates a new playback session for the given track (no description).
     */
    @Override
    public JukeSessionEntry create(String trackName) {
        return create(trackName, null);
    }

    /**
     * Creates a new playback session for the given track with an optional description.
     *
     * @param trackName   the recording name (maps to a ZIP file under the juke path)
     * @param description optional human-readable label for this run (e.g. "Normal replay")
     * @return the newly created session entry
     */
    @Override
    public JukeSessionEntry create(String trackName, String description) {
        String zipBasePath = ConfigUtil.getJukePath();
        String zipPath = zipBasePath + File.separator + trackName + ".zip";

        File zipFile = new File(zipPath);
        if (!zipFile.exists()) {
            throw new JukeAccessException("Track ZIP not found: " + zipPath);
        }

        JukeStorage dao = new JukeZipDAOImpl(zipBasePath, trackName);
        String sessionId = UUID.randomUUID().toString();
        JukeSessionEntry entry = new JukeSessionEntry(sessionId, trackName, dao, Instant.now(), description);
        sessions.put(sessionId, entry);

        LOG.info("Created Juke session {} for track '{}' (description: '{}', ZIP: {})",
                sessionId, trackName, description, zipPath);
        return entry;
    }

    /**
     * Validates that a session exists and matches the given track name.
     *
     * @param sessionId the session UUID
     * @param trackName the expected track name
     * @return {@code true} if the session is valid
     */
    @Override
    public boolean isValid(String sessionId, String trackName) {
        JukeSessionEntry entry = sessions.get(sessionId);
        return entry != null && entry.getTrackName().equals(trackName);
    }

    /**
     * Retrieves a session entry by ID.
     *
     * @param sessionId the session UUID
     * @return the session entry, or empty if not found
     */
    @Override
    public Optional<JukeSessionEntry> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Invalidates (removes) a session and moves it to the completed-sessions
     * history so the report endpoint can include it.
     *
     * @param sessionId the session UUID to remove
     */
    @Override
    public void invalidate(String sessionId) {
        JukeSessionEntry removed = sessions.remove(sessionId);
        if (removed != null) {
            removed.setStoppedAt(Instant.now());
            completedByTrack
                    .computeIfAbsent(removed.getTrackName(), k -> new CopyOnWriteArrayList<>())
                    .add(removed);
            LOG.info("Invalidated Juke session {} (track '{}', {} calls recorded)",
                    sessionId, removed.getTrackName(), removed.getCallHistory().size());
        }
    }

    /**
     * Evicts sessions older than the configured TTL.
     * <p>
     * This method should be called periodically (e.g., via {@code @Scheduled})
     * to clean up sessions from Playwright tests that crashed before calling
     * {@code /service/session/stop}.
     */
    @Override
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(SESSION_TTL);
        Iterator<Map.Entry<String, JukeSessionEntry>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, JukeSessionEntry> mapEntry = it.next();
            if (mapEntry.getValue().getCreatedAt().isBefore(cutoff)) {
                LOG.info("Evicting expired Juke session {} (track '{}', created {})",
                        mapEntry.getKey(),
                        mapEntry.getValue().getTrackName(),
                        mapEntry.getValue().getCreatedAt());
                it.remove();
            }
        }
    }

    /**
     * Returns the number of active sessions (useful for monitoring/testing).
     */
    @Override
    public int size() {
        return sessions.size();
    }

    @Override
    public Collection<JukeSessionEntry> snapshot() {
        return Collections.unmodifiableCollection(new ArrayList<>(sessions.values()));
    }

    /**
     * Returns all completed (invalidated) sessions for the given track, in the
     * order they were stopped. The list is a point-in-time snapshot.
     */
    @Override
    public List<JukeSessionEntry> getCompletedSessions(String trackName) {
        CopyOnWriteArrayList<JukeSessionEntry> list = completedByTrack.get(trackName);
        if (list == null || list.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(list));
    }
}
