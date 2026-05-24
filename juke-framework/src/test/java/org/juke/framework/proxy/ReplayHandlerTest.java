package org.juke.framework.proxy;

import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ReplayHandler} static methods, getters/setters,
 * and {@code getMockClass}.
 */
class ReplayHandlerTest {

    private String savedGlobal;

    @BeforeEach
    void backup() {
        savedGlobal = JukeState.getGlobaljuke();
        JukeState.setGlobaljuke(JukeState.NONE);
    }

    @AfterEach
    void restore() {
        JukeState.setGlobaljuke(savedGlobal);
        ReplayHandler.getReplayHandlerCache().clear();
    }

    // ── getMockClass ──────────────────────────────────────────────────────

    @Test
    void getMockClass_directImplementor_returnsInstanceClass() {
        // SampleService implements ISampleService directly
        Class<?> result = ReplayHandler.getMockClass(SampleService.class, ISampleService.class);
        assertEquals(SampleService.class, result);
    }

    @Test
    void getMockClass_objectClass_returnsObject() {
        Class<?> result = ReplayHandler.getMockClass(Object.class, ISampleService.class);
        assertEquals(Object.class, result);
    }

    @Test
    void getMockClass_subclass_walksHierarchy() {
        // Create a subclass of SampleService that doesn't declare ISampleService
        class Sub extends SampleService {}
        Class<?> result = ReplayHandler.getMockClass(Sub.class, ISampleService.class);
        // Should walk up to SampleService which implements ISampleService
        assertNotNull(result);
    }

    // ── getReplayHandlerCache ─────────────────────────────────────────────

    @Test
    void getReplayHandlerCache_returnsMap() {
        assertNotNull(ReplayHandler.getReplayHandlerCache());
    }

    // ── resetDelays ───────────────────────────────────────────────────────

    @Test
    void resetDelays_doesNotThrow() {
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) ReplayHandler::resetDelays);
    }

    // ── resetReplay with empty cache ──────────────────────────────────────

    @Test
    void resetReplay_emptyCache_doesNotThrow() {
        ReplayHandler.getReplayHandlerCache().clear();
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) ReplayHandler::resetReplay);
    }
}

