package org.juke.framework.session;

import org.juke.framework.storage.JukeStorage;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.metadata.JukeStateBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Immutable server-side record holding the {@link JukeStorage} DAO and
 * per-interface {@link DataProgramSchedule} instances for one active
 * cookie-based test session.
 * <p>
 * Each Playwright test session gets its own {@code JukeSessionEntry},
 * ensuring that concurrent sessions do not share any mutable replay state.
 */
public final class JukeSessionEntry {

    private final String sessionId;
    private final String trackName;
    /** Optional human-readable description set by the caller on {@code /service/session/start}. */
    private final String description;
    private final JukeStorage dao;
    private final Instant createdAt;
    /** Set when the session is invalidated; used by the report endpoint. */
    private volatile Instant stoppedAt;

    /**
     * Per-interface schedule built lazily on first access.
     * Thread-safe because multiple request threads sharing the same session
     * may call {@link #getScheduleFor(Class)} concurrently.
     */
    private final ConcurrentHashMap<Class<?>, DataProgramSchedule> schedulesByInterface =
            new ConcurrentHashMap<>();

    /**
     * Sequenced ZIP-entry key (e.g. {@code com.example.IGreetingsService.$greeting.7})
     * of the most recent replay call resolved for this session. Updated by
     * {@link #recordCall(String, Instant)} on the request thread after the
     * replay handler picks the next response. {@code volatile} so the status
     * controller's polling thread sees a consistent snapshot.
     */
    private volatile String lastCalledKey;
    private volatile Instant lastCalledAt;

    /**
     * Full per-call history (thread-safe, append-only). Each entry captures
     * the method, sequence number, recorded arguments, actual arguments sent
     * by the test, and whether they matched — used by the session report.
     */
    private final CopyOnWriteArrayList<CallRecord> callHistory = new CopyOnWriteArrayList<>();

    // ── CallRecord ───────────────────────────────────────────────────────

    /**
     * One replay invocation captured in the session's call history.
     *
     * @param sequence          1-based sequence number of this call in the recording
     * @param method            method name (e.g. {@code greeting})
     * @param recordedArguments arguments stored in the {@code .args.json} sidecar
     * @param actualArguments   arguments the test actually passed to the proxy
     * @param inputMatched      {@code true} when every recorded arg equals the actual arg
     * @param at                timestamp of the call
     */
    public record CallRecord(
            int sequence,
            String method,
            List<Object> recordedArguments,
            List<Object> actualArguments,
            boolean inputMatched,
            Instant at
    ) {}

    // ── Constructors ─────────────────────────────────────────────────────

    /** Legacy constructor — {@code description} defaults to {@code null}. */
    public JukeSessionEntry(String sessionId, String trackName, JukeStorage dao, Instant createdAt) {
        this(sessionId, trackName, dao, createdAt, null);
    }

    public JukeSessionEntry(String sessionId, String trackName, JukeStorage dao, Instant createdAt,
                            String description) {
        this.sessionId = sessionId;
        this.trackName = trackName;
        this.dao = dao;
        this.createdAt = createdAt;
        this.description = description;
    }

    // ── Schedule ─────────────────────────────────────────────────────────

    /**
     * Returns the replay schedule for the given interface, building it lazily
     * from the ZIP entry names on first access.
     *
     * @param interfaceClass the proxy interface class
     * @return a {@link DataProgramSchedule} scoped to this session
     */
    public DataProgramSchedule getScheduleFor(Class<?> interfaceClass) {
        return schedulesByInterface.computeIfAbsent(interfaceClass, clazz -> {
            Set<String> fileNames = dao.getFileNames();
            JukeStateBuilder built = new JukeStateBuilder.Builder(fileNames).build();
            return built.getSchedule();
        });
    }

    // ── Accessors ────────────────────────────────────────────────────────

    public String getSessionId() { return sessionId; }
    public String getTrackName() { return trackName; }
    /** Returns the optional test description, or {@code null} if none was set. */
    public String getDescription() { return description; }
    public JukeStorage getDao() { return dao; }
    public Instant getCreatedAt() { return createdAt; }
    /** Returns the instant this session was invalidated, or {@code null} if still active. */
    public Instant getStoppedAt() { return stoppedAt; }
    public void setStoppedAt(Instant stoppedAt) { this.stoppedAt = stoppedAt; }

    /**
     * Returns a live, read-only view of the per-interface schedules that have
     * been materialised for this session so far. Used by status/monitoring
     * callers to report step progress.
     */
    public Collection<DataProgramSchedule> getSchedules() {
        return Collections.unmodifiableCollection(schedulesByInterface.values());
    }

    // ── Call recording ───────────────────────────────────────────────────

    /**
     * Records the most recent replay call (basic form — no argument comparison data).
     * Adds a {@link CallRecord} with empty recorded/actual args and
     * {@code inputMatched=true} so callers that do not have args sidecar data
     * can still contribute to the history without inflating the deviation count.
     *
     * @param sequencedKey the resolved entry key including its sequence suffix;
     *                     {@code null} is ignored
     * @param at           the timestamp at which the call was resolved
     */
    public void recordCall(String sequencedKey, Instant at) {
        if (sequencedKey == null) return;
        this.lastCalledKey = sequencedKey;
        this.lastCalledAt = at;
        int seq = extractSequenceFromKey(sequencedKey);
        String method = extractMethodFromKey(sequencedKey);
        callHistory.add(new CallRecord(seq, method, List.of(), List.of(), true, at));
    }

    /**
     * Records a replay call with full argument-comparison data.
     *
     * @param sequencedKey  the resolved entry key; {@code null} is ignored
     * @param at            timestamp of the call
     * @param methodName    method name extracted from the key
     * @param sequence      1-based sequence number
     * @param recordedArgs  arguments stored in the {@code .args.json} sidecar
     * @param actualArgs    arguments the test actually passed
     * @param inputMatched  {@code true} when every arg matches the recording
     */
    public void recordCall(String sequencedKey, Instant at, String methodName, int sequence,
                           List<Object> recordedArgs, List<Object> actualArgs, boolean inputMatched) {
        if (sequencedKey == null) return;
        this.lastCalledKey = sequencedKey;
        this.lastCalledAt = at;
        callHistory.add(new CallRecord(
                sequence, methodName,
                safeCopy(recordedArgs),
                safeCopy(actualArgs),
                inputMatched,
                at));
    }

    /** @return the sequenced ZIP-entry key of the most recent replay call, or {@code null}. */
    public String getLastCalledKey() { return lastCalledKey; }
    /** @return the timestamp of the most recent replay call, or {@code null}. */
    public Instant getLastCalledAt() { return lastCalledAt; }

    /** Returns an unmodifiable view of the per-call history for this session. */
    public List<CallRecord> getCallHistory() {
        return Collections.unmodifiableList(new ArrayList<>(callHistory));
    }

    // ── Private helpers ──────────────────────────────────────────────────

    /** Null-safe, null-preserving defensive copy that produces an unmodifiable list. */
    private static List<Object> safeCopy(List<Object> list) {
        if (list == null || list.isEmpty()) return List.of();
        List<Object> copy = new ArrayList<>(list.size());
        copy.addAll(list);
        return Collections.unmodifiableList(copy);
    }

    /** Extracts the numeric sequence from a key like {@code IFoo.$bar.3} → {@code 3}. */
    public static int extractSequenceFromKey(String key) {
        try {
            String[] parts = key.split("\\.");
            return Integer.parseInt(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Extracts the method name from a key like {@code IFoo.$bar.3} → {@code bar}.
     * Strips a leading {@code $} if present (as written by the record handler).
     */
    public static String extractMethodFromKey(String key) {
        String[] parts = key.split("\\.");
        if (parts.length >= 2) {
            String name = parts[parts.length - 2];
            return name.startsWith("$") ? name.substring(1) : name;
        }
        return key;
    }
}
