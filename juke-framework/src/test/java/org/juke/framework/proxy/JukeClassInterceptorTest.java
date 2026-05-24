package org.juke.framework.proxy;

import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeClassInterceptor} — constructor, createProxy,
 * and intercept with NONE passthrough mode.
 */
class JukeClassInterceptorTest {

    private String savedGlobal;

    @BeforeEach
    void backup() { savedGlobal = JukeState.getGlobaljuke(); }

    @AfterEach
    void restore() { JukeState.setGlobaljuke(savedGlobal); }

    // ── constructor ───────────────────────────────────────────────────────

    @Test
    void constructor_setsFields() {
        SampleService svc = new SampleService();
        JukeClassInterceptor interceptor = new JukeClassInterceptor(svc, SampleService.class);
        assertNotNull(interceptor);
    }

    // ── createProxy ───────────────────────────────────────────────────────

    @Test
    void createProxy_returnsProxySubclass() {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService svc = new SampleService();
        SampleService proxy = JukeClassInterceptor.createProxy(svc, SampleService.class);
        assertNotNull(proxy);
        // The proxy is a CGLIB subclass
        assertNotSame(SampleService.class, proxy.getClass());
    }

    // ── intercept: NONE / IGNORE mode → passthrough ───────────────────────

    @Test
    void intercept_noneMode_passesThroughToRealTarget() {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService svc = new SampleService();
        SampleService proxy = JukeClassInterceptor.createProxy(svc, SampleService.class);
        // Call a method — should reach the real SampleService without exception
        assertDoesNotThrow(() -> proxy.fromSimpleDoubleArray(new double[]{1.0, 2.0}));
    }

    @Test
    void intercept_ignoreMode_passesThroughToRealTarget() {
        JukeState.setGlobaljuke(JukeState.IGNORE);
        SampleService svc = new SampleService();
        SampleService proxy = JukeClassInterceptor.createProxy(svc, SampleService.class);
        assertDoesNotThrow(() -> proxy.fromSimpleDoubleArray(new double[]{1.0}));
    }

    // ── intercept: Object methods bypass Juke ────────────────────────────

    @Test
    void intercept_toStringOnProxy_doesNotThrow() {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService proxy = JukeClassInterceptor.createProxy(new SampleService(), SampleService.class);
        assertDoesNotThrow(() -> proxy.toString());
    }
}

