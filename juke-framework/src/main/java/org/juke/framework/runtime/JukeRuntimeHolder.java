package org.juke.framework.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Thread-safe singleton slot for the "current" {@link JukeRuntime} and the
 * single source of truth for Juke's runtime state.
 * <p>
 * Callers read the active runtime via {@link #current()}, mutate it atomically
 * via {@link #update(UnaryOperator)} or {@link #set(JukeRuntime)}, and can
 * scope a runtime to a block via {@link #withRuntime(JukeRuntime, Runnable)}.
 * <p>
 * Design notes:
 * <ul>
 *   <li>The slot uses {@link AtomicReference} for lock-free swaps; no
 *       {@code synchronized} blocks, so we never pin virtual threads.</li>
 *   <li>The default is {@link JukeRuntime#NONE} (passthrough) — safe to
 *       call from any thread before bootstrap completes.</li>
 *   <li>This class is intentionally final and non-instantiable.</li>
 * </ul>
 */
public final class JukeRuntimeHolder {

    private static final AtomicReference<JukeRuntime> CURRENT =
            new AtomicReference<>(JukeRuntime.NONE);

    private JukeRuntimeHolder() {
        // no instances
    }

    /**
     * @return the currently installed runtime; never {@code null}.
     *         Defaults to {@link JukeRuntime#NONE} if nothing has been set.
     */
    public static JukeRuntime current() {
        return CURRENT.get();
    }

    /**
     * Atomically installs a new runtime as the current one.
     *
     * @param runtime the new runtime; {@code null} is rejected — pass
     *                {@link JukeRuntime#NONE} for passthrough
     * @return the previous runtime
     */
    public static JukeRuntime set(JukeRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return CURRENT.getAndSet(runtime);
    }

    /**
     * Resets the holder to {@link JukeRuntime#NONE}. Primarily for tests so
     * that per-test setup does not leak into the next test.
     */
    public static void reset() {
        CURRENT.set(JukeRuntime.NONE);
    }

    /**
     * Atomically applies {@code op} to the current runtime and installs the
     * result. Used by the legacy setters ({@code JukeState.setGlobaljuke},
     * {@code JukeHelper.setJukeDao}, …) to mutate individual fields while
     * keeping {@link JukeRuntime} immutable.
     *
     * @param op a function that produces the new runtime; must not return
     *           {@code null}
     * @return the updated runtime
     */
    public static JukeRuntime update(UnaryOperator<JukeRuntime> op) {
        Objects.requireNonNull(op, "op");
        return CURRENT.updateAndGet(cur ->
                Objects.requireNonNull(op.apply(cur), "updated runtime"));
    }

    /**
     * Temporarily installs {@code runtime} for the duration of {@code body},
     * then restores whatever was there before — including if {@code body}
     * throws.
     * <p>
     * Intended for test scopes. Not for production use: a test harness that
     * needs concurrent session isolation should pass a {@link JukeRuntime}
     * explicitly rather than relying on this holder.
     */
    public static void withRuntime(JukeRuntime runtime, Runnable body) {
        JukeRuntime previous = set(runtime);
        try {
            body.run();
        } finally {
            set(previous);
        }
    }
}
