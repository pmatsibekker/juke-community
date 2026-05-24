package org.juke.framework.proxy;

import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link TemplateMethodInterceptor} — constructor, accessors,
 * and invoke passthrough (IGNORE state).
 */
class TemplateMethodInterceptorTest {

    private String savedGlobal;

    @BeforeEach
    void backup() { savedGlobal = JukeState.getGlobaljuke(); }

    @AfterEach
    void restore() { JukeState.setGlobaljuke(savedGlobal); }

    // ── constructor + accessors ───────────────────────────────────────────

    @Test
    void constructor_setsRealTemplateAndName() {
        SampleService svc = new SampleService();
        TemplateMethodInterceptor tmi = new TemplateMethodInterceptor(
                svc, "MySvc", "ignore", new String[0]);
        assertSame(svc, tmi.getRealTemplate());
        assertEquals("MySvc", tmi.getTemplateName());
    }

    @Test
    void constructor_withNullExtraExclusions_doesNotThrow() {
        SampleService svc = new SampleService();
        assertDoesNotThrow(() ->
                new TemplateMethodInterceptor(svc, "MySvc", "ignore", null));
    }

    // ── invoke: toString (excluded method) ───────────────────────────────

    @Test
    void invoke_toStringPassesThrough() throws Exception {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService svc = new SampleService();
        TemplateMethodInterceptor tmi = new TemplateMethodInterceptor(
                svc, "Svc", JukeState.IGNORE, new String[0]);

        // Build a JDK proxy backed by the interceptor for ISampleService
        ISampleService proxy = JukeProxyFactory.createInterfaceProxy(ISampleService.class, tmi);
        // toString is in the excluded set — should pass through to real template
        assertDoesNotThrow(() -> proxy.toString());
    }

    // ── invoke: IGNORE / NONE state → passthrough ─────────────────────────

    @Test
    void invoke_ignoreState_passesThroughToRealTemplate() throws Exception {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService svc = new SampleService();
        TemplateMethodInterceptor tmi = new TemplateMethodInterceptor(
                svc, "Svc", JukeState.IGNORE, new String[0]);

        ISampleService proxy = JukeProxyFactory.createInterfaceProxy(ISampleService.class, tmi);
        // fromSimpleDoubleArray is not in the excluded set; state = IGNORE → passthrough
        assertDoesNotThrow(() -> proxy.fromSimpleDoubleArray(new double[]{1.0, 2.0}));
    }

    // ── invoke: JUKE state with global=IGNORE → passthrough ──────────────

    @Test
    void invoke_jukeStateWithIgnoreGlobal_passesThroughToRealTemplate() throws Exception {
        JukeState.setGlobaljuke(JukeState.IGNORE);
        SampleService svc = new SampleService();
        TemplateMethodInterceptor tmi = new TemplateMethodInterceptor(
                svc, "Svc", JukeState.JUKE, new String[0]);

        ISampleService proxy = JukeProxyFactory.createInterfaceProxy(ISampleService.class, tmi);
        assertDoesNotThrow(() -> proxy.fromSimpleDoubleArray(new double[]{1.0}));
    }

    // ── invoke: extra exclusions list ────────────────────────────────────

    @Test
    void invoke_extraExcludedMethod_passesThroughWithoutInterception() throws Exception {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService svc = new SampleService();
        // Exclude fromSimpleDoubleArray explicitly
        TemplateMethodInterceptor tmi = new TemplateMethodInterceptor(
                svc, "Svc", JukeState.RECORD, new String[]{"fromSimpleDoubleArray"});

        ISampleService proxy = JukeProxyFactory.createInterfaceProxy(ISampleService.class, tmi);
        // Should pass through instead of trying to record
        assertDoesNotThrow(() -> proxy.fromSimpleDoubleArray(new double[]{1.0}));
    }
}

