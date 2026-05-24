package org.juke.framework.storage;

import java.util.List;
import java.util.Set;

/**
 * Storage abstraction for Juke recordings. Implementations persist entries
 * (JSON blobs, metadata, and sidecars) keyed by identifier, and read them
 * back on replay.
 *
 * <p>Phase 6 (Dependency Inversion): all framework collaborators — handlers,
 * runtimes, helpers, session registry — depend on this interface rather than
 * a concrete implementation. The ZIP-backed {@link JukeZipDAOImpl} is the
 * default; a future in-memory or remote-store implementation can plug in
 * without changes elsewhere.
 *
 * <p>This interface was formerly named {@code JukeAPI}; the name was changed
 * for clarity since it is specifically a storage port.
 *
 * <h2>Two abstraction levels</h2>
 *
 * The interface mixes two scopes:
 *
 * <ul>
 *   <li><b>Per-recording</b> (the original methods: {@link #readFromFile},
 *       {@link #writeToFile}, {@link #write}, {@link #path},
 *       {@link #getFileNames}, {@link #asString}, ...) — operate on the
 *       single ZIP this {@code JukeStorage} instance was opened against.
 *       Used by handlers and runtimes during a record/replay session.</li>
 *   <li><b>Recording-store</b> ({@link #listRecordings},
 *       {@link #loadRecording}, {@link #saveRecording}) — operate on the
 *       collection of recordings the storage backend manages. Phase 5.A
 *       additions per MIGRATION_PLAN.md §6 — used by the admin host /
 *       agent to enumerate and shuttle recordings as opaque ZIP blobs
 *       between hosts and the admin server.</li>
 * </ul>
 *
 * <p>An implementation may support either or both. The folder-backed
 * {@link JukeZipDAOImpl} supports both — the folder it is rooted at is
 * the recording store; the specific ZIP it is opened against (when one
 * is supplied) is the per-recording handle. A test stub or session-
 * scoped instance may legitimately implement only the per-recording
 * methods and let the recording-store defaults raise
 * {@link UnsupportedOperationException}.
 */
public interface JukeStorage {

    // ── Per-recording API ────────────────────────────────────────────
    //
    // Default implementations raise UnsupportedOperationException so a
    // recording-store-only impl (e.g. JukeJpaStorageBackend, the §5.C
    // application-wide bean) does not need to stub each method.
    // Session-scoped impls (JukeZipDAOImpl bound to one ZIP) override
    // them with the actual per-recording I/O.

    default <T> T readFromFile(Class<T> c, String identifier) {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    /**
     * Reads a recorded entry and deserializes it using the specified runtime type
     * instead of the declared return type from JukeClass metadata.
     * This handles cases where the declared return type is Object but the actual
     * recorded type is a concrete class (e.g., RestTemplate.getForEntity).
     *
     * @param interfaceClass the proxy interface class (for JukeClassMap lookup)
     * @param identifier     the sequenced entry identifier
     * @param runtimeType    the actual class to deserialize into
     * @param <T>            the target type
     * @return the deserialized object
     */
    default <T> T readFromFileAsType(Class<?> interfaceClass, String identifier, Class<T> runtimeType) {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    default boolean writeToFile(String identifier, String o) {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    /**
     * Writes an entry with an exact key name (no auto-incrementing of sequence numbers).
     * The key should be the complete entry name including .json extension.
     */
    default void writeDirectEntry(String exactKey, String content) {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    /**
     * Returns the current sequence index for the given base identifier,
     * or 0 if no entries have been recorded for it yet.
     */
    default int getCurrentSequence(String identifier) {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    default String write() {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    default String path() {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    default Set<String> getFileNames() {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    default String asString(String identifier) {
        throw new UnsupportedOperationException(perRecordingMessage());
    }

    private String perRecordingMessage() {
        return getClass().getSimpleName() + " does not support per-recording I/O; "
                + "this impl is scoped to the recording store, not a single recording";
    }

    // ── Recording-store API (Phase 5.A) ──────────────────────────────
    //
    // These methods promote the SPI from per-recording to per-store.
    // Default implementations raise UnsupportedOperationException so
    // legacy per-recording impls remain valid; folder- and DB-backed
    // stores override them.

    /**
     * Enumerate every recording the backend currently manages, in an
     * implementation-defined order. Names are the same identifiers
     * accepted by {@link #loadRecording(String)} and
     * {@link #saveRecording(String, byte[])}; the {@code .zip} suffix
     * is normalised away by callers when they appear.
     *
     * @return live list of recording names; never null
     * @throws UnsupportedOperationException by default — implementations
     *     that scope to a single recording (e.g. a session-bound
     *     {@code JukeZipDAOImpl}) do not provide this method
     */
    default List<String> listRecordings() {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support recording-store enumeration; "
                        + "this impl is scoped to a single recording");
    }

    /**
     * Read the entire ZIP archive for the named recording as a byte
     * array. Used by the agent's {@code /service/recordings/{name}}
     * endpoint and by the admin server's recording-aggregation pull.
     *
     * @param name recording identifier as returned by {@link #listRecordings()}
     * @return ZIP bytes; never null
     * @throws UnsupportedOperationException by default
     * @throws JukeStorageException if the named recording does not exist
     *     or cannot be read
     */
    default byte[] loadRecording(String name) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support recording-store loads; "
                        + "this impl is scoped to a single recording");
    }

    /**
     * Persist the given ZIP bytes under the named recording. Overwrites
     * an existing recording with the same name.
     *
     * @param name  recording identifier
     * @param bytes ZIP bytes; non-null and non-empty
     * @throws UnsupportedOperationException by default
     * @throws JukeStorageException if the bytes cannot be written
     */
    default void saveRecording(String name, byte[] bytes) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " does not support recording-store writes; "
                        + "this impl is scoped to a single recording");
    }
}
