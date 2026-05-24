package org.juke.framework.annotation;

import org.juke.framework.proxy.JukeState;
import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeInitializer} – covers normalizeJukeState switch branches,
 * initializeAnnotatedFields, and processConstructorParameters.
 */
class JukeInitializerTest {

    private String savedGlobal;

    @BeforeEach
    void backup() { savedGlobal = JukeState.getGlobaljuke(); }

    @AfterEach
    void restore() { JukeState.setGlobaljuke(savedGlobal); }

    // ── normalizeJukeState ────────────────────────────────────────────────

    @Test
    void normalizeJukeState_null_returnsJuke() {
        assertEquals(JukeState.JUKE, JukeInitializer.normalizeJukeState(null));
    }

    @Test
    void normalizeJukeState_empty_returnsJuke() {
        assertEquals(JukeState.JUKE, JukeInitializer.normalizeJukeState("  "));
    }

    @Test
    void normalizeJukeState_juke() {
        assertEquals(JukeState.JUKE, JukeInitializer.normalizeJukeState("juke"));
    }

    @Test
    void normalizeJukeState_record() {
        assertEquals(JukeState.RECORD, JukeInitializer.normalizeJukeState("record"));
    }

    @Test
    void normalizeJukeState_replay() {
        assertEquals(JukeState.REPLAY, JukeInitializer.normalizeJukeState("REPLAY"));
    }

    @Test
    void normalizeJukeState_ignore() {
        assertEquals(JukeState.IGNORE, JukeInitializer.normalizeJukeState("Ignore"));
    }

    @Test
    void normalizeJukeState_none() {
        assertEquals(JukeState.NONE, JukeInitializer.normalizeJukeState("none"));
    }

    @Test
    void normalizeJukeState_emptyString_returnsJuke() {
        // empty string triggers the null/empty guard at the top → returns JUKE
        assertEquals(JukeState.JUKE, JukeInitializer.normalizeJukeState(""));
    }

    @Test
    void normalizeJukeState_disable() {
        assertEquals(JukeState.DISABLE, JukeInitializer.normalizeJukeState("disable"));
    }

    @Test
    void normalizeJukeState_unknown_returnsJuke() {
        assertEquals(JukeState.JUKE, JukeInitializer.normalizeJukeState("unknown-value"));
    }

    // ── wrap ──────────────────────────────────────────────────────────────

    @Test
    void wrap_nullOriginal_returnsNull() {
        ISampleService result = JukeInitializer.wrap(null, ISampleService.class);
        assertNull(result);
    }

    @Test
    void wrap_nonNullOriginal_returnsWrapped() {
        ISampleService svc = new SampleService();
        ISampleService wrapped = JukeInitializer.wrap(svc, ISampleService.class);
        assertNotNull(wrapped);
    }

    @Test
    void wrap_withState_doesNotThrow() {
        ISampleService svc = new SampleService();
        assertDoesNotThrow(() ->
                JukeInitializer.wrap(svc, ISampleService.class, JukeState.IGNORE));
    }

    // ── initializeAnnotatedFields ─────────────────────────────────────────

    @Test
    void initializeAnnotatedFields_null_doesNotThrow() {
        assertDoesNotThrow(() -> JukeInitializer.initializeAnnotatedFields(null));
    }

    static class NoAnnotationBean {
        public String value = "plain";
    }

    @Test
    void initializeAnnotatedFields_noAnnotations_leavesFieldsUnchanged() {
        NoAnnotationBean bean = new NoAnnotationBean();
        JukeInitializer.initializeAnnotatedFields(bean);
        assertEquals("plain", bean.value);
    }

    static class JukeFieldBean {
        @Juke
        public ISampleService service;
    }

    @Test
    void initializeAnnotatedFields_nullFieldValue_skips() {
        JukeFieldBean bean = new JukeFieldBean();
        bean.service = null;
        assertDoesNotThrow(() -> JukeInitializer.initializeAnnotatedFields(bean));
        assertNull(bean.service);
    }

    @Test
    void initializeAnnotatedFields_nonNullInterfaceField_wraps() {
        JukeFieldBean bean = new JukeFieldBean();
        bean.service = new SampleService();
        JukeInitializer.initializeAnnotatedFields(bean);
        assertNotNull(bean.service);
    }

    // ── processConstructorParameters ─────────────────────────────────────

    @Test
    void processConstructorParameters_nullValues_returnsNull() {
        Object[] result = JukeInitializer.processConstructorParameters(NoAnnotationBean.class, (Object[]) null);
        assertNull(result);
    }

    @Test
    void processConstructorParameters_emptyValues_returnsEmpty() {
        Object[] result = JukeInitializer.processConstructorParameters(NoAnnotationBean.class);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    void processConstructorParameters_noMatchingConstructor_returnsOriginal() {
        Object[] args = new Object[]{"a", "b"};
        Object[] result = JukeInitializer.processConstructorParameters(NoAnnotationBean.class, args);
        assertArrayEquals(args, result);
    }

    static class BeanWithJukeParam {
        public ISampleService service;
        public BeanWithJukeParam(@Juke ISampleService svc) {
            this.service = svc;
        }
    }

    @Test
    void processConstructorParameters_withJukeParam_wrapsIt() {
        ISampleService svc = new SampleService();
        Object[] result = JukeInitializer.processConstructorParameters(BeanWithJukeParam.class, svc);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertNotNull(result[0]);
    }
}

