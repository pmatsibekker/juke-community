package org.juke.framework.spring;

import org.juke.framework.annotation.Juke;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.storage.ZipUtil;
import org.juke.framework.support.SampleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A field-level {@code @Juke} on a <b>concrete</b> type is wrapped in a CGLIB
 * subclass (not rejected), and the {@link Juke#name()} and
 * {@link Juke#excludeMethods()} attributes work — the single way to intercept a
 * concrete dependency (e.g. a {@code RestTemplate}).
 */
class JukeConcreteFieldTest {

    private static final String ZIP = "juke-concrete-test";

    private final JukeBeanPostProcessor processor = new JukeBeanPostProcessor();
    private String savedGlobal;

    @BeforeEach
    void setUp() {
        savedGlobal = JukeState.getGlobaljuke();
        JukeRuntimeHolder.reset();
        System.setProperty("juke.path", ConfigUtil.getDefauljukePath());
        System.setProperty("juke.zip", ZIP);
        File zip = new File(ConfigUtil.getDefauljukePath(), ZIP + ".zip");
        if (zip.exists()) {
            //noinspection ResultOfMethodCallIgnored
            zip.delete();
        }
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
        JukeRuntimeHolder.reset();
    }

    static class ShippingBean {
        @Juke(name = "shipping", excludeMethods = {"toSimpleDoubleArray"})
        public SampleService svc;     // concrete, non-final
    }

    @Test
    void concreteField_wrapsViaCglib_recordsUnderName_andSkipsExcludedMethod() throws Exception {
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(ConfigUtil.getDefauljukePath(), ZIP));

        ShippingBean bean = new ShippingBean();
        bean.svc = new SampleService();
        processor.postProcessAfterInitialization(bean, "shippingBean");

        // Wrapped as an assignable CGLIB subclass, not a JDK proxy, not the raw bean.
        assertTrue(bean.svc instanceof SampleService);
        assertNotSame(SampleService.class, bean.svc.getClass());
        assertFalse(Proxy.isProxyClass(bean.svc.getClass()));

        // Delegates to the real bean (record mode calls through and captures).
        Double[] recorded = bean.svc.fromSimpleDoubleArray(new double[]{1.0, 2.0});
        assertNotNull(recorded);
        // Excluded method passes through without being recorded.
        double[] excluded = bean.svc.toSimpleDoubleArray(new Double[]{3.0});
        assertNotNull(excluded);

        String writtenPath = JukeHelper.getJukeDAO().write();
        Set<String> entries = ZipUtil.getFileNamesFromZipFile(writtenPath);

        assertTrue(entries.stream().anyMatch(e -> e.startsWith("shipping.fromSimpleDoubleArray.")),
                "recording should use the @Juke(name) prefix; entries: " + entries);
        assertTrue(entries.stream().noneMatch(e -> e.contains("toSimpleDoubleArray")),
                "@Juke(excludeMethods) method must not be recorded; entries: " + entries);
    }
}
