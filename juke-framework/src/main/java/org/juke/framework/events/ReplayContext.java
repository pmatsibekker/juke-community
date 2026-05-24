package org.juke.framework.events;

/**
 * Per-thread carrier for the run / use-case / recording identifiers that
 * scope a single replay execution. The Phase 4 Playwright runner pushes a
 * context onto this ThreadLocal before invoking the test, and the framework's
 * proxy + AOP advice attach those identifiers to outbound events
 * ({@link ScenarioEventSink}).
 *
 * <p>If no context is set (e.g. dev-mode replay run from an IDE), the
 * framework's instrumentation hooks short-circuit because there is nowhere
 * meaningful to attribute the result.
 *
 * <pre>
 * ReplayContext.set(new ReplayContext.Scope(runId, useCaseId, recordingId));
 * try {
 *     // ... run the test ...
 * } finally {
 *     ReplayContext.clear();
 * }
 * </pre>
 */
public final class ReplayContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private ReplayContext() {}

    public static Scope current() {
        return CURRENT.get();
    }

    public static void set(Scope scope) {
        if (scope == null) CURRENT.remove();
        else CURRENT.set(scope);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Per-call recording step. Optional — set by the proxy when known. */
    public static int incrementStep() {
        Scope s = CURRENT.get();
        if (s == null) return 0;
        s.step++;
        return s.step;
    }

    /**
     * Mutable scope object. {@link #step} is incremented by the proxy on each
     * intercepted call; {@code runId / useCaseId / recordingId} are set once
     * by the runner.
     */
    public static final class Scope {
        public final Long runId;
        public final Long useCaseId;
        public final Long recordingId;
        public int step;

        public Scope(Long runId, Long useCaseId, Long recordingId) {
            this.runId = runId;
            this.useCaseId = useCaseId;
            this.recordingId = recordingId;
            this.step = 0;
        }
    }
}
