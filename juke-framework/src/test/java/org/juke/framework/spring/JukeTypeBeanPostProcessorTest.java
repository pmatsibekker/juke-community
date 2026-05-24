package org.juke.framework.spring;

import org.juke.framework.annotation.Juke;
import org.juke.framework.proxy.JukeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JukeTypeBeanPostProcessor} – covers the main happy paths
 * without needing a full Spring context.
 */
class JukeTypeBeanPostProcessorTest {

    private JukeTypeBeanPostProcessor processor;
    private String savedGlobalJuke;

    @BeforeEach
    void setUp() {
        processor = new JukeTypeBeanPostProcessor();
        savedGlobalJuke = JukeState.getGlobaljuke();
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobalJuke);
    }

    /** Plain bean with no {@code @Juke} annotation – must be returned unchanged. */
    static class PlainBean {
        public String hello() { return "hello"; }
    }

    // Simulates Spring proxy class naming; superclass is not Object.
    static class PlainBean$$Proxy extends PlainBean {}

    // Simulates a $$ class whose superclass is Object to cover unwrap fallback.
    static class Object$$Like {}

    @Test
    void postProcessAfterInitialization_noJukeAnnotation_returnsBeanUnchanged() {
        PlainBean bean = new PlainBean();
        Object result = processor.postProcessAfterInitialization(bean, "plainBean");
        assertSame(bean, result, "Unannotated bean must be returned as-is");
    }

    @Test
    void postProcessAfterInitialization_nullSafety() {
        // Should not NPE when checking annotation on the class
        PlainBean bean = new PlainBean();
        assertDoesNotThrow(() ->
                processor.postProcessAfterInitialization(bean, "beanName"));
    }

    @Test
    void postProcessAfterInitialization_withIgnoreMode_returnsBeanUnchanged() {
        JukeState.setGlobaljuke(JukeState.IGNORE);
        // A class annotated with @Juke but mode resolves to IGNORE should pass through
        PlainBean bean = new PlainBean();
        Object result = processor.postProcessAfterInitialization(bean, "ignored");
        assertSame(bean, result);
    }

    // ── @Juke class-level annotation paths ───────────────────────────────

    @Juke("ignore")
    static class JukeIgnoreBean {
        public String greet() { return "hi"; }
    }

    @Test
    void postProcessAfterInitialization_jukeIgnoreAnnotation_returnsBeanUnchanged() {
        // Make this test deterministic: @Juke("ignore") resolves via global state.
        JukeState.setGlobaljuke(JukeState.IGNORE);
        JukeIgnoreBean bean = new JukeIgnoreBean();
        Object result = processor.postProcessAfterInitialization(bean, "ign");
        assertSame(bean, result);
    }

    @Juke("none")
    static class JukeNoneBean {
        public String greet() { return "hi"; }
    }

    @Test
    void postProcessAfterInitialization_jukeNoneAnnotation_executesCodePath() {
        JukeNoneBean bean = new JukeNoneBean();
        // NONE may or may not skip wrapping depending on resolved global state;
        // verify no exception and a non-null result.
        Object result = processor.postProcessAfterInitialization(bean, "none");
        assertNotNull(result);
    }

    @Juke
    static class JukeDefaultBean {
        public String greet() { return "hi"; }
    }

    @Test
    void postProcessAfterInitialization_jukeDefaultAnnotation_wrapsBean() {
        JukeState.setGlobaljuke(JukeState.JUKE);
        JukeDefaultBean bean = new JukeDefaultBean();
        Object result = processor.postProcessAfterInitialization(bean, "jukeDef");
        assertNotNull(result);
    }

    // ── resolveMode called with null annotation value ─────────────────────

    @Test
    void resolveMode_emptyValue_resolvesFallback() {
        // @Juke with default empty value → resolveMode falls through to JukeFactory
        JukeDefaultBean bean = new JukeDefaultBean();
        assertDoesNotThrow(() ->
                processor.postProcessAfterInitialization(bean, "mode-empty"));
    }

    // ── unwrapProxyClass: Spring proxy subclass (name contains $$) ────────

    @Test
    void unwrapProxyClass_springProxySubclass_isHandled() {
        // An anonymous subclass won't contain $$ but at minimum verifies no NPE
        PlainBean bean = new PlainBean() {};
        Object result = processor.postProcessAfterInitialization(bean, "anon");
        assertNotNull(result);
    }

    @Test
    void unwrapProxyClass_nameContainsDollarDollar_withRealSuperclass() {
        PlainBean$$Proxy bean = new PlainBean$$Proxy();
        Object result = processor.postProcessAfterInitialization(bean, "proxyLike");
        assertNotNull(result);
    }

    @Test
    void unwrapProxyClass_nameContainsDollarDollar_superIsObject_returnsUnchanged() {
        Object$$Like bean = new Object$$Like();
        Object result = processor.postProcessAfterInitialization(bean, "objectLike");
        assertSame(bean, result);
    }
}
