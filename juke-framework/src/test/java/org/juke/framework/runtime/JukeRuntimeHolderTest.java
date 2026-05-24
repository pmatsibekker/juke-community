package org.juke.framework.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.juke.framework.proxy.JukeState;

class JukeRuntimeHolderTest {

    @AfterEach
    void resetHolderAndStatics() {
        JukeRuntimeHolder.reset();
        JukeState.setGlobaljuke(null);
    }

    @Test
    void defaultsToNoneSentinel() {
        JukeRuntimeHolder.reset();
        assertSame(JukeRuntime.NONE, JukeRuntimeHolder.current());
    }

    @Test
    void setReturnsPreviousAndInstallsNew() {
        JukeRuntimeHolder.reset();
        JukeRuntime next = JukeRuntime.builder().mode(JukeMode.IGNORE).build();
        JukeRuntime previous = JukeRuntimeHolder.set(next);

        assertSame(JukeRuntime.NONE, previous);
        assertSame(next, JukeRuntimeHolder.current());
    }

    @Test
    void setRejectsNull() {
        assertThrows(NullPointerException.class, () -> JukeRuntimeHolder.set(null));
    }

    @Test
    void withRuntimeRestoresOnNormalReturn() {
        JukeRuntime outer = JukeRuntime.builder().mode(JukeMode.IGNORE).build();
        JukeRuntimeHolder.set(outer);

        JukeRuntime inner = JukeRuntime.builder().mode(JukeMode.NONE).build();
        JukeRuntimeHolder.withRuntime(inner,
                () -> assertSame(inner, JukeRuntimeHolder.current()));

        assertSame(outer, JukeRuntimeHolder.current());
    }

    @Test
    void withRuntimeRestoresOnException() {
        JukeRuntime outer = JukeRuntime.builder().mode(JukeMode.IGNORE).build();
        JukeRuntimeHolder.set(outer);

        try {
            JukeRuntimeHolder.withRuntime(
                    JukeRuntime.builder().mode(JukeMode.NONE).build(),
                    () -> { throw new IllegalStateException("boom"); });
            fail("expected propagation of runtime exception");
        } catch (IllegalStateException expected) {
            // expected
        }

        assertSame(outer, JukeRuntimeHolder.current());
    }

    @Test
    void legacySetterMirrorsIntoHolder() {
        JukeRuntimeHolder.reset();
        JukeState.setGlobaljuke(JukeState.REPLAY);
        assertEquals(JukeMode.REPLAY, JukeRuntimeHolder.current().mode());
    }

    @Test
    void update_appliesOperatorAndReturnsNewRuntime() {
        JukeRuntimeHolder.reset();
        JukeRuntime updated = JukeRuntimeHolder.update(rt -> rt.withMode(JukeMode.IGNORE));
        assertEquals(JukeMode.IGNORE, updated.mode());
        assertSame(updated, JukeRuntimeHolder.current());
        JukeRuntimeHolder.reset();
    }

    @Test
    void update_rejectsNullOperator() {
        assertThrows(NullPointerException.class, () -> JukeRuntimeHolder.update(null));
    }
}
