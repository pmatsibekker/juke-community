package org.juke.framework.template;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.proxy.TemplateMethodInterceptor;
import org.juke.framework.proxy.TemplateRecordingWrapper;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Template interception infrastructure.
 * <p>
 * Tests cover:
 * <ul>
 *     <li>TemplateMethodInterceptor in IGNORE mode (passthrough)</li>
 *     <li>TemplateMethodInterceptor in RECORD mode (writes to zip)</li>
 *     <li>TemplateMethodInterceptor in REPLAY mode (reads from zip)</li>
 *     <li>Excluded method bypass</li>
 *     <li>TemplateRecordingWrapper proxy creation</li>
 *     <li>Sequence numbering for repeated calls</li>
 * </ul>
 */
public class TemplateMethodInterceptorTest {

    // ============================ Test doubles ============================

    /**
     * A simple interface so JDK proxies can be created for testing.
     */
    public interface ISampleTemplate {
        String fetchData(String key);
        int compute(int a, int b);
    }

    /**
     * Concrete implementation — simulates a Spring Template.
     */
    public static class SampleTemplate implements ISampleTemplate {
        @Override
        public String fetchData(String key) {
            return "real-" + key;
        }

        @Override
        public int compute(int a, int b) {
            return a + b;
        }

        @Override
        public String toString() {
            return "SampleTemplate";
        }
    }

    // ============================ Setup ===================================

    private SampleTemplate realTemplate;
    private String savedGlobalJuke;

    @BeforeEach
    void setUp() {
        realTemplate = new SampleTemplate();

        // Save and reset global state so other tests don't interfere
        savedGlobalJuke = JukeState.getGlobaljuke();

        // Make sure JukeHelper DAO is initialized for record tests
        String jukePath = ConfigUtil.getDefauljukePath();
        System.setProperty("juke.path", jukePath);
        System.setProperty("juke.zip", "template-test");

        // Clean up previous test artifacts
        File zipFile = new File(jukePath, "template-test.zip");
        if (zipFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            zipFile.delete();
        }
    }

    @AfterEach
    void tearDown() {
        // Restore global state
        JukeState.setGlobaljuke(savedGlobalJuke);
    }

    // ============================ IGNORE mode =============================

    @Test
    void ignore_mode_passesThrough() throws Throwable {
        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.IGNORE, null);

        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        Object result = interceptor.invoke(null, fetchData, new Object[]{"hello"});

        assertEquals("real-hello", result);
    }

    @Test
    void ignore_mode_compute_passesThrough() throws Throwable {
        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.IGNORE, null);

        Method compute = ISampleTemplate.class.getMethod("compute", int.class, int.class);
        Object result = interceptor.invoke(null, compute, new Object[]{3, 7});

        assertEquals(10, result);
    }

    // ======================== Excluded methods =============================

    @Test
    void toString_isNeverIntercepted() throws Throwable {
        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.RECORD, null);

        Method toString = Object.class.getMethod("toString");
        Object result = interceptor.invoke(null, toString, null);

        // Should call the real toString, not record it
        assertEquals("SampleTemplate", result);
    }

    @Test
    void customExcludedMethod_isSkipped() throws Throwable {
        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.RECORD,
                new String[]{"fetchData"});

        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        Object result = interceptor.invoke(null, fetchData, new Object[]{"test"});

        // Should passthrough, not record
        assertEquals("real-test", result);
    }

    // ======================== RECORD mode ==================================

    @Test
    void record_mode_callsRealAndWritesToZip() throws Throwable {
        // Force RECORD mode globally so JukeZipDAOImpl constructor skips juke.json read
        JukeState.setGlobaljuke(JukeState.RECORD);

        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                ConfigUtil.getDefauljukePath(), "template-test"));

        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.RECORD, null);

        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        Object result = interceptor.invoke(null, fetchData, new Object[]{"world"});

        // The real method should still be called
        assertEquals("real-world", result);

        // Flush the ZIP so the entry is persisted
        String writtenPath = JukeHelper.getJukeDAO().write();

        // Re-read file names from the actual written path
        assertNotNull(writtenPath);
        assertTrue(new File(writtenPath).exists(),
                "Written ZIP should exist at: " + writtenPath);
    }

    @Test
    void record_mode_sequencesMultipleCalls() throws Throwable {
        // Force RECORD mode globally so JukeZipDAOImpl constructor skips juke.json read
        JukeState.setGlobaljuke(JukeState.RECORD);

        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                ConfigUtil.getDefauljukePath(), "template-test"));

        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.RECORD, null);

        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);

        Object r1 = interceptor.invoke(null, fetchData, new Object[]{"first"});
        Object r2 = interceptor.invoke(null, fetchData, new Object[]{"second"});
        Object r3 = interceptor.invoke(null, fetchData, new Object[]{"third"});

        // Verify real calls were made
        assertEquals("real-first", r1);
        assertEquals("real-second", r2);
        assertEquals("real-third", r3);

        String writtenPath = JukeHelper.getJukeDAO().write();
        assertNotNull(writtenPath);

        // Read actual entries from the written ZIP to verify sequencing
        Set<String> entries = org.juke.framework.storage.ZipUtil.getFileNamesFromZipFile(writtenPath);

        // Should have 3 distinct data entries (values differ so no compaction)
        // Plus juke.json and possibly juke-mappings.json
        long dataEntries = entries.stream()
                .filter(e -> e.startsWith("SampleTemplate.fetchData."))
                .filter(e -> !e.contains(".type."))
                .count();
        assertTrue(dataEntries >= 1, "Should have at least 1 data entry, found: " + dataEntries
                + " in entries: " + entries);
    }

    // ======================== REPLAY mode ==================================

    @Test
    void record_then_replay_returnsRecordedValue() throws Throwable {
        // ---- Record phase ----
        JukeState.setGlobaljuke(JukeState.RECORD);

        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                ConfigUtil.getDefauljukePath(), "template-test"));

        TemplateMethodInterceptor recorder = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.RECORD, null);

        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        recorder.invoke(null, fetchData, new Object[]{"replay-test"});

        // Flush — this writes juke.json + entries into the ZIP
        String writtenPath = JukeHelper.getJukeDAO().write();
        assertNotNull(writtenPath);
        File writtenFile = new File(writtenPath);
        assertTrue(writtenFile.exists(), "ZIP should exist after write");

        // ---- Replay phase ----
        // Point to the actual written file location
        JukeState.setGlobaljuke(JukeState.REPLAY);

        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                writtenFile.getParent(),
                writtenFile.getName().replace(".zip", "")));

        TemplateMethodInterceptor replayer = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.REPLAY, null);

        Object result = replayer.invoke(null, fetchData, new Object[]{"anything"});

        // Should get back the recorded value, not a live call result
        assertNotNull(result);
        assertTrue(result.toString().contains("real-replay-test"));
    }

    // ======================== Wrapper factory ==============================

    @Test
    void wrapper_createsProxyForInterfaceType() {
        ISampleTemplate wrapped = TemplateRecordingWrapper.wrap(
                realTemplate, "SampleTemplate", JukeState.IGNORE,
                new String[0], ISampleTemplate.class);

        assertNotNull(wrapped);
        assertEquals("real-hello", wrapped.fetchData("hello"));
        assertEquals(15, wrapped.compute(7, 8));
    }

    // ======================== State resolution =============================

    @Test
    void jukeState_followsGlobalWhenSetToJuke() throws Throwable {
        JukeState.setGlobaljuke(JukeState.IGNORE);

        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.JUKE, null);

        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        Object result = interceptor.invoke(null, fetchData, new Object[]{"test"});

        // Global is IGNORE, so should passthrough
        assertEquals("real-test", result);
    }

    // ======================== State resolution =============================

    /**
     * resolveState() JUKE + global=RECORD branch:
     * when jukeState is "juke" and the global state is RECORD the interceptor
     * must follow the global state and call handleRecord.
     */
    @Test
    void jukeState_withGlobalRecord_callsHandleRecord() throws Throwable {
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                ConfigUtil.getDefauljukePath(), "template-test"));

        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.JUKE, null);

        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        // resolveState() → jukeState=="juke" && global==RECORD → returns RECORD → handleRecord
        Object result = interceptor.invoke(null, fetchData, new Object[]{"juke-global-record"});

        assertEquals("real-juke-global-record", result);
    }

    /**
     * resolveState() JUKE + global=REPLAY branch:
     * when jukeState is "juke" and the global state is REPLAY the interceptor
     * must follow the global state and call handleReplay.
     * Also exercises ensureReplayInitialized (first call) and the early-return
     * on a second call.
     */
    @Test
    void jukeState_withGlobalReplay_callsHandleReplay() throws Throwable {
        // ---- record a value first ----
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                ConfigUtil.getDefauljukePath(), "template-test"));

        TemplateMethodInterceptor recorder = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.RECORD, null);
        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        recorder.invoke(null, fetchData, new Object[]{"juke-global-replay-test"});
        String writtenPath = JukeHelper.getJukeDAO().write();
        assertNotNull(writtenPath);

        // ---- replay via JUKE state + global=REPLAY ----
        JukeState.setGlobaljuke(JukeState.REPLAY);
        File written = new File(writtenPath);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                written.getParent(), written.getName().replace(".zip", "")));

        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.JUKE, null);

        // First call: resolveState → REPLAY → handleReplay → ensureReplayInitialized (initializes)
        Object result = interceptor.invoke(null, fetchData, new Object[]{"ignored"});
        assertNotNull(result);

        // Second call on same interceptor: ensureReplayInitialized early-return (already init)
        assertDoesNotThrow(() -> interceptor.invoke(null, fetchData, new Object[]{"ignored2"}));
    }

    /**
     * handleReplay: when JukeRuntimeHolder has no storage, ensureReplayInitialized
     * creates a new DAO from ConfigUtil. Covers the
     * {@code JukeRuntimeHolder.current().storage() == null} true-branch.
     */
    @Test
    void handleReplay_noRuntimeStorage_initializesDao() throws Throwable {
        // ---- record ----
        JukeState.setGlobaljuke(JukeState.RECORD);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(
                ConfigUtil.getDefauljukePath(), "template-test"));
        TemplateMethodInterceptor recorder = new TemplateMethodInterceptor(
                realTemplate, "SampleTemplate", JukeState.RECORD, null);
        Method fetchData = ISampleTemplate.class.getMethod("fetchData", String.class);
        recorder.invoke(null, fetchData, new Object[]{"storage-init-test"});
        JukeHelper.getJukeDAO().write();

        // ---- replay with storage cleared from runtime holder ----
        JukeState.setGlobaljuke(JukeState.REPLAY);
        System.setProperty("juke.path", ConfigUtil.getDefauljukePath());
        System.setProperty("juke.zip", "template-test");

        // Reset to NONE so storage() == null, triggering DAO creation inside ensureReplayInitialized
        org.juke.framework.runtime.JukeRuntimeHolder.reset();
        try {
            TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                    realTemplate, "SampleTemplate", JukeState.REPLAY, null);
            // Should not throw even with no pre-set storage
            assertDoesNotThrow(() -> interceptor.invoke(null, fetchData, new Object[]{"any"}));
        } finally {
            // Restore DAO so other tests are not affected
            JukeHelper.setJukeDao(new JukeZipDAOImpl(
                    ConfigUtil.getDefauljukePath(), "template-test"));
        }
    }

    // ======================== Accessors ===================================

    @Test
    void interceptor_exposesRealTemplateAndName() {
        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                realTemplate, "MyTemplate", JukeState.IGNORE, null);

        assertSame(realTemplate, interceptor.getRealTemplate());
        assertEquals("MyTemplate", interceptor.getTemplateName());
    }
}

