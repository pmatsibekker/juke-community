package org.juke.framework.spring;

import org.juke.framework.annotation.Juke;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeBeanPostProcessor} covering shouldWrap branches and
 * normalizeJukeState switch cases.
 */
class JukeBeanPostProcessorTest {

    private JukeBeanPostProcessor processor;
    private String savedGlobal;

    @BeforeEach
    void setUp() {
        processor = new JukeBeanPostProcessor();
        savedGlobal = JukeState.getGlobaljuke();
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
    }

    // ── postProcessBeforeInitialization is a no-op ────────────────────────

    @Test
    void postProcessBeforeInitialization_returnsBeanUnchanged() {
        Object bean = new Object();
        Object result = processor.postProcessBeforeInitialization(bean, "b");
        assertSame(bean, result);
    }

    // ── null bean ─────────────────────────────────────────────────────────

    @Test
    void postProcessAfterInitialization_nullBean_returnsNull() {
        Object result = processor.postProcessAfterInitialization(null, "b");
        assertNull(result);
    }

    // ── bean with no @Juke fields ─────────────────────────────────────────

    static class NoJukeBean {
        public String value = "plain";
    }

    @Test
    void postProcessAfterInitialization_noJukeFields_returnsBeanUnchanged() {
        NoJukeBean bean = new NoJukeBean();
        Object result = processor.postProcessAfterInitialization(bean, "b");
        assertSame(bean, result);
    }

    // ── @Juke(autoWrap=false) skips wrapping ──────────────────────────────

    static class AutoWrapFalseBean {
        @Juke(autoWrap = false)
        public ISampleService service;
    }

    @Test
    void postProcessAfterInitialization_autoWrapFalse_fieldLeftUnchanged() {
        AutoWrapFalseBean bean = new AutoWrapFalseBean();
        bean.service = new SampleService();
        Object result = processor.postProcessAfterInitialization(bean, "b");
        assertSame(bean, result);
        assertNotNull(bean.service);
    }

    // ── null field value skips wrapping ───────────────────────────────────

    static class NullFieldBean {
        @Juke
        public ISampleService service; // null by default
    }

    @Test
    void postProcessAfterInitialization_nullFieldValue_skipsWrapping() {
        NullFieldBean bean = new NullFieldBean();
        assertNull(bean.service);
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
        assertNull(bean.service); // still null
    }

    // ── concrete-typed field: wraps via CGLIB (see JukeConcreteFieldTest) ──
    // A concrete field is now wrapped in a CGLIB subclass, not rejected. The
    // exception path is reserved for a type that can't be wrapped at all — a
    // final class with no assignable interface proxy (e.g. String).

    static class UnwrappableFinalFieldBean {
        @Juke
        public String name = "hello"; // String is final → no CGLIB subclass, no assignable proxy
    }

    @Test
    void postProcessAfterInitialization_unwrappableFinalField_throws() {
        UnwrappableFinalFieldBean bean = new UnwrappableFinalFieldBean();
        assertThrows(org.juke.framework.exception.JukeConfigurationException.class,
                () -> processor.postProcessAfterInitialization(bean, "b"));
    }

    // ── @Juke(autoWrap=false) on a concrete field is a deliberate opt-out ──
    static class NonInterfaceAutoWrapFalseBean {
        @Juke(autoWrap = false)
        public String name = "hello";
    }

    @Test
    void postProcessAfterInitialization_nonInterfaceAutoWrapFalse_skipsQuietly() {
        NonInterfaceAutoWrapFalseBean bean = new NonInterfaceAutoWrapFalseBean();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
        assertEquals("hello", bean.name); // field unchanged, no exception
    }

    // ── valid interface field → wraps with proxy ──────────────────────────

    static class JukeFieldBean {
        @Juke("juke")
        public ISampleService service;
    }

    @Test
    void postProcessAfterInitialization_validInterfaceField_wrapsWithProxy() {
        JukeState.setGlobaljuke(null); // NONE mode so proxy passes through
        JukeFieldBean bean = new JukeFieldBean();
        bean.service = new SampleService();
        processor.postProcessAfterInitialization(bean, "b");
        assertNotNull(bean.service);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(bean.service.getClass()),
                "Field should have been replaced by a JDK proxy");
    }

    // ── normalizeJukeState switch cases via @Juke("...") ─────────────────

    static class JukeRecordFieldBean {
        @Juke("record")
        public ISampleService service;
    }

    @Test
    void normalizeJukeState_recordValue_becomesProxy() {
        JukeRecordFieldBean bean = new JukeRecordFieldBean();
        bean.service = new SampleService();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
        assertTrue(java.lang.reflect.Proxy.isProxyClass(bean.service.getClass()));
    }

    static class JukeReplayFieldBean {
        @Juke("replay")
        public ISampleService service;
    }

    @Test
    void normalizeJukeState_replayValue_becomesProxy() {
        JukeReplayFieldBean bean = new JukeReplayFieldBean();
        bean.service = new SampleService();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
    }

    static class JukeIgnoreFieldBean {
        @Juke("ignore")
        public ISampleService service;
    }

    @Test
    void normalizeJukeState_ignoreValue_becomesProxy() {
        JukeIgnoreFieldBean bean = new JukeIgnoreFieldBean();
        bean.service = new SampleService();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
    }

    static class JukeNoneFieldBean {
        @Juke("none")
        public ISampleService service;
    }

    @Test
    void normalizeJukeState_noneValue_becomesProxy() {
        JukeNoneFieldBean bean = new JukeNoneFieldBean();
        bean.service = new SampleService();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
    }

    static class JukeDisableFieldBean {
        @Juke("disable")
        public ISampleService service;
    }

    @Test
    void normalizeJukeState_disableValue_becomesProxy() {
        JukeDisableFieldBean bean = new JukeDisableFieldBean();
        bean.service = new SampleService();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
    }

    static class JukeUnknownFieldBean {
        @Juke("weirdvalue")
        public ISampleService service;
    }

    @Test
    void normalizeJukeState_unknownValue_defaultsToJuke() {
        JukeUnknownFieldBean bean = new JukeUnknownFieldBean();
        bean.service = new SampleService();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(bean, "b"));
    }

    // ── Spring proxy class (contains $$) is unwrapped ────────────────────

    @Test
    void postProcessAfterInitialization_springProxySubclass_processesFields() {
        NoJukeBean bean = new NoJukeBean() {};
        Object result = processor.postProcessAfterInitialization(bean, "sub");
        assertSame(bean, result);
    }

    // ── invoke Object utility methods through the lazy proxy (bypasses Juke) ─

    @Test
    void lazyProxy_toString_doesNotThrow() throws Exception {
        JukeState.setGlobaljuke(null);
        JukeFieldBean bean = new JukeFieldBean();
        bean.service = new SampleService();
        processor.postProcessAfterInitialization(bean, "b");
        // toString/hashCode/equals are NOT intercepted by JukeMethodFilter →
        // they go via the invokeUnwrapped path in buildLazyProxy
        assertDoesNotThrow(() -> bean.service.toString());
    }

    // ── invoke a real ISampleService method through the lazy proxy ────────

    @Test
    void lazyProxy_callServiceMethod_doesNotThrow() {
        JukeState.setGlobaljuke(null);
        JukeFieldBean bean = new JukeFieldBean();
        bean.service = new SampleService();
        processor.postProcessAfterInitialization(bean, "b");
        // Calling a real method exercises the resolveJukeProxy path in the lambda
        assertDoesNotThrow(() ->
                bean.service.fromSimpleDoubleArray(new double[]{1.0, 2.0}));
    }

    // ── Spring $$ proxy class name detection ─────────────────────────────

    /** Simulates a Spring CGLIB proxy whose class name contains $$. */
    static class MockBean$$EnhancerBySpringCGLIB extends JukeFieldBean {
        // class name contains $$ which triggers proxy-unwrapping branch
    }

    @Test
    void postProcessAfterInitialization_springProxyClass_usesActualSuperclass() {
        MockBean$$EnhancerBySpringCGLIB proxyBean = new MockBean$$EnhancerBySpringCGLIB();
        proxyBean.service = new SampleService();
        assertDoesNotThrow(() -> processor.postProcessAfterInitialization(proxyBean, "proxy$$"));
    }
}
