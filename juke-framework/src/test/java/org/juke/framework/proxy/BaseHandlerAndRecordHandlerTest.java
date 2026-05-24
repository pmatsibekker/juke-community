package org.juke.framework.proxy;

import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.support.ISampleService;
import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BaseHandler} MDC token infrastructure, getters/setters,
 * and {@link RecordHandler} constructor / newInstance.
 */
class BaseHandlerAndRecordHandlerTest {

    // ---- Inline test doubles ----

    public interface IPingService {
        String ping();
    }

    public static class PingService implements IPingService {
        @Override public String ping() { return "pong"; }
    }

    public interface ITypedService {
        Object fetch(String key, Class<?> type);
    }

    public static class TypedService implements ITypedService {
        @Override public Object fetch(String key, Class<?> type) { return "result-" + key; }
    }

    private String savedGlobal;

    @BeforeEach
    void backup() { savedGlobal = JukeState.getGlobaljuke(); }

    @AfterEach
    void restore() {
        JukeState.setGlobaljuke(savedGlobal);
        MDC.remove(BaseHandler.MDC_JUKE_METHOD);
        MDC.remove(BaseHandler.MDC_JUKE_TARGET);
    }

    // ── BaseHandler getters/setters ───────────────────────────────────────

    @Test
    void baseHandler_gettersSetters() {
        RecordHandler<ISampleService> handler = new RecordHandler<>();
        SampleService svc = new SampleService();
        handler.setService(svc);
        assertSame(svc, handler.getService());

        handler.setInterfaceClass(ISampleService.class);
        assertSame(ISampleService.class, handler.getInterfaceClass());

        handler.setInitialized(true);
        assertTrue(handler.isInitialized());

        JukeClass jc = JukeConfigBuilder.set(ISampleService.class).build();
        handler.setJukeClass(jc);
        assertSame(jc, handler.getJukeClass());
    }

    // ── pushInvocationMdc / popInvocationMdc ─────────────────────────────

    @Test
    void pushAndPopMdc_withMethod_setsAndRestoresKeys() throws Exception {
        RecordHandler<ISampleService> handler = new RecordHandler<>();
        handler.setInterfaceClass(ISampleService.class);

        Method m = ISampleService.class.getMethod("getMyDataMap",
                java.math.BigDecimal[].class, String.class);

        // Should start with empty MDC keys
        assertNull(MDC.get(BaseHandler.MDC_JUKE_METHOD));

        BaseHandler.MdcToken token = handler.pushInvocationMdc(m);
        assertNotNull(token);
        assertEquals("getMyDataMap", MDC.get(BaseHandler.MDC_JUKE_METHOD));
        assertEquals("ISampleService", MDC.get(BaseHandler.MDC_JUKE_TARGET));

        handler.popInvocationMdc(token);
        assertNull(MDC.get(BaseHandler.MDC_JUKE_METHOD));
        assertNull(MDC.get(BaseHandler.MDC_JUKE_TARGET));
    }

    @Test
    void pushInvocationMdc_nullMethod_doesNotSetMethodKey() throws Exception {
        RecordHandler<ISampleService> handler = new RecordHandler<>();
        handler.setInterfaceClass(ISampleService.class);

        BaseHandler.MdcToken token = handler.pushInvocationMdc(null);
        assertNotNull(token);
        // method key not set because method was null
        assertNull(MDC.get(BaseHandler.MDC_JUKE_METHOD));

        handler.popInvocationMdc(token);
    }

    @Test
    void popInvocationMdc_nullToken_doesNotThrow() {
        RecordHandler<ISampleService> handler = new RecordHandler<>();
        assertDoesNotThrow(() -> handler.popInvocationMdc(null));
    }

    // ── RecordHandler constructor (service + class registration) ─────────

    @Test
    void recordHandler_constructor_registersJukeClass() {
        // Clear any existing registration
        JukeClass.instance().remove(ISampleService.class.getCanonicalName());

        SampleService svc = new SampleService();
        RecordHandler<ISampleService> handler = new RecordHandler<>(svc, ISampleService.class);
        assertNotNull(handler.getService());
        assertEquals(ISampleService.class, handler.getInterfaceClass());
    }

    @Test
    void recordHandler_constructor_existingJukeClass_noException() {
        // Ensure the class is already registered
        JukeClass jc = JukeConfigBuilder.set(ISampleService.class).build();
        JukeClass.instance().put(ISampleService.class.getCanonicalName(), jc);

        SampleService svc = new SampleService();
        assertDoesNotThrow(() -> new RecordHandler<>(svc, ISampleService.class));
    }

    // ── RecordHandler.newInstance ─────────────────────────────────────────

    @Test
    void recordHandler_newInstance_returnsProxy() {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService svc = new SampleService();
        RecordHandler<ISampleService> handler = new RecordHandler<>(svc, ISampleService.class);
        ISampleService proxy = handler.newInstance(svc, ISampleService.class);
        assertNotNull(proxy);
        assertTrue(java.lang.reflect.Proxy.isProxyClass(proxy.getClass()));
    }

    // ---- RecordHandler.invoke toString path ----

    @Test
    void recordHandler_invoke_toStringBypassesRecording() throws Throwable {
        JukeState.setGlobaljuke(JukeState.NONE);
        SampleService svc = new SampleService();
        RecordHandler<ISampleService> handler = new RecordHandler<>(svc, ISampleService.class);
        ISampleService proxy = handler.newInstance(svc, ISampleService.class);

        // toString() should not attempt record — just return a string representation
        assertDoesNotThrow(() -> proxy.toString());
    }

    // ---- RecordHandler.invoke in RECORD mode (writeSidecars path) ----

    @Test
    void recordHandler_invoke_normalMethod_coversWriteSidecarsPath(@TempDir Path tempDir) {
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(tempDir.toString(), "rh-test"));

        SampleService svc = new SampleService();
        RecordHandler<ISampleService> handler = new RecordHandler<>(svc, ISampleService.class);
        ISampleService proxy = handler.newInstance(svc, ISampleService.class);

        // fromSimpleDoubleArray returns Double[] (non-null), an interface method → writeSidecars
        Double[] result = proxy.fromSimpleDoubleArray(new double[]{1.0, 2.0, 3.0});
        assertNotNull(result);
        assertEquals(3, result.length);
    }

    @Test
    void recordHandler_invoke_noArgMethod_coversArgsNullBranch(@TempDir Path tempDir) {
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(tempDir.toString(), "noarg-test"));

        PingService svc = new PingService();
        RecordHandler<IPingService> handler = new RecordHandler<>(svc, IPingService.class);
        IPingService proxy = handler.newInstance(svc, IPingService.class);

        // ping() has no args — JDK proxy passes null for args → covers args==null branch
        String result = proxy.ping();
        assertEquals("pong", result);
    }

    @Test
    void recordHandler_invoke_classArgMethod_coversTypeDiscriminatorBranch(@TempDir Path tempDir) {
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(tempDir.toString(), "typed-test"));

        TypedService svc = new TypedService();
        RecordHandler<ITypedService> handler = new RecordHandler<>(svc, ITypedService.class);
        ITypedService proxy = handler.newInstance(svc, ITypedService.class);

        // fetch(String, Class<?>) has a Class arg → typeDiscriminator != null → writeSidecars type branch
        Object result = proxy.fetch("mykey", String.class);
        assertNotNull(result);
    }
}

