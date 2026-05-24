package org.juke.framework.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.storage.JukeStorage;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.storage.Marshaller;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Immutable value object bundling everything Juke needs to record or replay
 * a single interaction stream.
 * <p>
 * Historically, Juke stored this state in a scatter of {@code static} fields
 * (the old {@code JukeState.GLOBAL_JUKE}, {@code JukeHelper.jukeDAO}, …).
 * That prevented two tests — let alone two concurrent sessions — from running
 * at the same time. {@code JukeRuntime} is the replacement: one instance
 * per session, held by {@link JukeRuntimeHolder}, and mutated via copy-with
 * helpers such as {@link #withMode(JukeMode)} / {@link #withStorage(JukeStorage)}.
 * <p>
 * Instances are <b>immutable</b>: all primary fields are final. The in-memory
 * caches ({@link #proxyCache()}, {@link #replayHandlerCache()},
 * {@link #metadataCache()}, {@link #tunerParticipants()}) are held by final
 * references to {@link ConcurrentHashMap}s so that copy-with helpers preserve
 * the same cache identity across runtime snapshots — otherwise toggling the
 * mode would blow away every memoised proxy.
 * <p>
 * To change state, build a new {@code JukeRuntime} (via {@link #builder()} or
 * a {@code withXxx} helper) and install it with
 * {@link JukeRuntimeHolder#update}.
 */
public final class JukeRuntime {

    /**
     * Sentinel runtime representing "Juke disabled — pass everything through
     * to the real service". Never records, never replays, never touches
     * storage. The caches are present but empty; because NONE itself never
     * triggers handler construction, they stay empty in practice.
     */
    public static final JukeRuntime NONE = new JukeRuntime(
            JukeMode.NONE, null, Marshaller.getMapper(), null, null,
            new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>(), new ConcurrentHashMap<>());

    private final JukeMode mode;
    private final JukeStorage storage;
    private final ObjectMapper marshaller;
    private final String trackPath;
    private final String trackName;

    // ---- Per-runtime caches (migrated from static fields in Phase 3 Step F)
    private final Map<Class<?>, Object> proxyCache;
    private final Map<Class<?>, Object> replayHandlerCache;
    private final Map<String, JukeClass> metadataCache;
    private final Map<String, Set<String>> tunerParticipants;

    /**
     * Full-control constructor. Prefer {@link #builder()} for new code.
     *
     * @param mode       active mode, never {@code null}
     * @param storage    DAO for this session, or {@code null} when {@code mode == NONE}
     * @param marshaller Jackson {@link ObjectMapper} to use for this session;
     *                   typically {@link Marshaller#getMapper()}
     * @param trackPath  directory holding the track ZIP (for diagnostics / rebuilds)
     * @param trackName  ZIP identifier (without {@code .zip} suffix)
     */
    public JukeRuntime(JukeMode mode,
                       JukeStorage storage,
                       ObjectMapper marshaller,
                       String trackPath,
                       String trackName) {
        this(mode, storage, marshaller, trackPath, trackName,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    private JukeRuntime(JukeMode mode,
                        JukeStorage storage,
                        ObjectMapper marshaller,
                        String trackPath,
                        String trackName,
                        Map<Class<?>, Object> proxyCache,
                        Map<Class<?>, Object> replayHandlerCache,
                        Map<String, JukeClass> metadataCache,
                        Map<String, Set<String>> tunerParticipants) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.storage = storage;
        this.marshaller = Objects.requireNonNull(marshaller, "marshaller");
        this.trackPath = trackPath;
        this.trackName = trackName;
        this.proxyCache = proxyCache;
        this.replayHandlerCache = replayHandlerCache;
        this.metadataCache = metadataCache;
        this.tunerParticipants = tunerParticipants;
    }

    /** @return the active mode for this session; never {@code null}. */
    public JukeMode mode() {
        return mode;
    }

    /** @return the DAO serving this session, or {@code null} for {@link #NONE}. */
    public JukeStorage storage() {
        return storage;
    }

    public ObjectMapper marshaller() {
        return marshaller;
    }

    public String trackPath() {
        return trackPath;
    }

    public String trackName() {
        return trackName;
    }

    /**
     * Proxies returned from {@code JukeFactory.newInstance} keyed by interface
     * class. Replaces the old {@code JukeFactory.jukeMockCache} static.
     */
    public Map<Class<?>, Object> proxyCache() {
        return proxyCache;
    }

    /**
     * {@code ReplayHandler} instances keyed by interface class. Replaces the
     * old {@code ReplayHandler.replayHandlerCache} static. Values are stored
     * as {@code Object} to avoid a circular dependency on the proxy package.
     */
    public Map<Class<?>, Object> replayHandlerCache() {
        return replayHandlerCache;
    }

    /**
     * {@link JukeClass} metadata keyed by canonical class name. Replaces the
     * old {@code JukeClass.instance()} static.
     */
    public Map<String, JukeClass> metadataCache() {
        return metadataCache;
    }

    /**
     * Tuner participant signatures keyed by tuner class name. Replaces the
     * old {@code TunerTask.participants} static.
     */
    public Map<String, Set<String>> tunerParticipants() {
        return tunerParticipants;
    }

    /** Convenience — true when this runtime is actively recording or replaying. */
    public boolean isActive() {
        return mode.isActive();
    }

    /** Returns a copy of this runtime with {@code newMode} in place of {@link #mode()}. */
    public JukeRuntime withMode(JukeMode newMode) {
        return new JukeRuntime(newMode, storage, marshaller, trackPath, trackName,
                proxyCache, replayHandlerCache, metadataCache, tunerParticipants);
    }

    /** Returns a copy of this runtime with {@code newStorage} in place of {@link #storage()}. */
    public JukeRuntime withStorage(JukeStorage newStorage) {
        return new JukeRuntime(mode, newStorage, marshaller, trackPath, trackName,
                proxyCache, replayHandlerCache, metadataCache, tunerParticipants);
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing a {@link JukeRuntime} without exposing
     * a telescoping constructor.
     */
    public static final class Builder {
        private JukeMode mode = JukeMode.NONE;
        private JukeStorage storage;
        private ObjectMapper marshaller = Marshaller.getMapper();
        private String trackPath;
        private String trackName;

        public Builder mode(JukeMode value) {
            this.mode = Objects.requireNonNull(value, "mode");
            return this;
        }

        public Builder storage(JukeStorage value) {
            this.storage = value;
            return this;
        }

        public Builder marshaller(ObjectMapper value) {
            this.marshaller = Objects.requireNonNull(value, "marshaller");
            return this;
        }

        public Builder track(String path, String name) {
            this.trackPath = path;
            this.trackName = name;
            return this;
        }

        /**
         * Builds the runtime, opening a default {@link JukeZipDAOImpl} against
         * {@code trackPath}/{@code trackName} if no storage was explicitly set
         * and the mode is active.
         */
        public JukeRuntime build() {
            JukeStorage effectiveStorage = storage;
            if (effectiveStorage == null && mode.isActive()
                    && trackPath != null && trackName != null) {
                try {
                    effectiveStorage = new JukeZipDAOImpl(trackPath, trackName);
                } catch (JukeAccessException e) {
                    // Surface as the typed storage exception; tests should
                    // not be silently left without a DAO.
                    throw e;
                }
            }
            return new JukeRuntime(mode, effectiveStorage, marshaller, trackPath, trackName);
        }
    }
}
