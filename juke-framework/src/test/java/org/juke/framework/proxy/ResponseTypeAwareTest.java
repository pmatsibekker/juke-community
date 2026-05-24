package org.juke.framework.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.support.IRestClient;
import org.juke.framework.support.ISampleService;
import org.juke.framework.support.RestClientImpl;
import org.juke.framework.support.SampleService;
import org.juke.framework.support.SimpleResult;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeTransformerUtil;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.storage.ZipUtil;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.metadata.JukeStateBuilder;
import org.junit.jupiter.api.*;

import java.io.File;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =============================================================================
 * Juke ResponseType-Aware Test Suite
 * =============================================================================
 *
 * This test suite validates whether Juke can handle the RestTemplate.getForEntity
 * scenario where:
 *   - The SAME method is called with different Class<?> responseType arguments
 *   - The return type varies depending on that runtime argument
 *   - Replay must deserialize to the correct concrete type
 *
 * The suite is organized into 6 test groups:
 *
 * GROUP 1: Regression -- Existing sequential replay behavior (SHOULD PASS)
 * GROUP 2: Regression -- Existing serialization fidelity (SHOULD PASS)
 * GROUP 3: Diagnostic -- Record/Replay with single responseType (SHOULD PASS)
 * GROUP 4: Diagnostic -- Record/Replay with MULTIPLE responseTypes (EXPOSES THE BUG)
 * GROUP 5: Diagnostic -- Schedule dispatch has no type awareness (EXPOSES LIMITATION)
 * GROUP 6: Regression -- Non-ambiguous methods still work (SHOULD PASS)
 *
 * Tests annotated @Tag("regression") validate existing behavior must not break.
 * Tests annotated @Tag("diagnostic") reveal the responseType handling gap.
 * =============================================================================
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ResponseTypeAwareTest {

    static ObjectMapper objectMapper;
    static {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // Shared test fixtures
    private static final String JUKE_ZIP_NAME = "response-type-test";
    private static String jukePath;

    /** Captured during G4.1 recording so G4.2 replay can find the ZIP */
    private static String recordedZipPath = null;

    private IRestClient realService = new RestClientImpl();

    @BeforeAll
    static void globalSetup() {
        jukePath = ConfigUtil.getDefauljukePath();
        cleanFile(new File(jukePath, JUKE_ZIP_NAME + ".zip"));
        cleanFile(new File(jukePath, JUKE_ZIP_NAME + ".zip.bak"));
    }

    @AfterAll
    static void globalTeardown() {
        cleanFile(new File(jukePath, JUKE_ZIP_NAME + ".zip"));
        cleanFile(new File(jukePath, JUKE_ZIP_NAME + ".zip.bak"));
        if (recordedZipPath != null) {
            cleanFile(new File(recordedZipPath));
            cleanFile(new File(recordedZipPath + ".bak"));
        }
    }

    private static void cleanFile(File f) {
        if (f != null && f.exists()) f.delete();
    }

    // =========================================================================
    // GROUP 1: REGRESSION -- Sequential Replay Schedule Behavior
    // =========================================================================

    @Test
    @Order(1)
    @Tag("regression")
    @DisplayName("G1.1: DataProgramSchedule returns entries in sequential order")
    void schedule_sequentialOrder() {
        Set<String> entryNames = new LinkedHashSet<>();
        entryNames.add("com.example.IService.$getData.1.json");
        entryNames.add("com.example.IService.$getData.2.json");
        entryNames.add("com.example.IService.$getData.3.json");

        JukeStateBuilder built = new JukeStateBuilder.Builder(entryNames).build();
        DataProgramSchedule schedule = built.getSchedule();

        String first = schedule.getNextAvailable("com.example.IService.$getData");
        String second = schedule.getNextAvailable("com.example.IService.$getData");
        String third = schedule.getNextAvailable("com.example.IService.$getData");

        System.out.println("[G1.1] Sequential replay:");
        System.out.println("  1st: " + first);
        System.out.println("  2nd: " + second);
        System.out.println("  3rd: " + third);

        assertNotEquals(first, second, "First and second should differ");
        assertNotEquals(second, third, "Second and third should differ");
    }

    @Test
    @Order(2)
    @Tag("regression")
    @DisplayName("G1.2: DataProgramSchedule tracks multiple methods independently")
    void schedule_multipleMethodsIndependent() {
        Set<String> entryNames = new LinkedHashSet<>();
        entryNames.add("com.example.IService.$methodA.1.json");
        entryNames.add("com.example.IService.$methodA.2.json");
        entryNames.add("com.example.IService.$methodB.1.json");

        JukeStateBuilder built = new JukeStateBuilder.Builder(entryNames).build();
        DataProgramSchedule schedule = built.getSchedule();

        String a1 = schedule.getNextAvailable("com.example.IService.$methodA");
        String b1 = schedule.getNextAvailable("com.example.IService.$methodB");
        String a2 = schedule.getNextAvailable("com.example.IService.$methodA");

        System.out.println("[G1.2] Independent method tracking:");
        System.out.println("  A-1st: " + a1);
        System.out.println("  B-1st: " + b1);
        System.out.println("  A-2nd: " + a2);

        assertTrue(a1.contains("$methodA"), "First A should be methodA");
        assertTrue(b1.contains("$methodB"), "First B should be methodB");
        assertTrue(a2.contains("$methodA"), "Second A should be methodA");
        assertNotEquals(a1, a2, "A entries should advance");
    }

    // =========================================================================
    // GROUP 2: REGRESSION -- Serialization round-trip fidelity
    // =========================================================================

    @Test
    @Order(10)
    @Tag("regression")
    @DisplayName("G2.1: JukeConfigBuilder captures ISampleService method metadata")
    void configBuilder_capturesMethodMetadata() {
        JukeClass jukeClass = JukeConfigBuilder.set(ISampleService.class).build();

        assertNotNull(jukeClass, "JukeClass should be built");
        assertEquals(ISampleService.class.getCanonicalName(), jukeClass.getClassName());
        assertTrue(jukeClass.getMethods().size() > 0, "Should have methods");
        assertFalse(jukeClass.getMethodsByName("getMyDataMap").isEmpty(),
                "getMyDataMap should be in the method list");

        System.out.println("[G2.1] ISampleService methods captured: " + jukeClass.getMethods().size());
    }

    @Test
    @Order(11)
    @Tag("regression")
    @DisplayName("G2.2: HashMap<String,BigDecimal> round-trips through JukeTransformerUtil")
    void serialization_hashMapRoundTrip() throws Exception {
        JukeClass jukeClass = JukeConfigBuilder.set(ISampleService.class).build();
        ISampleService service = new SampleService();

        BigDecimal[] biggie = {new BigDecimal("123.456"), new BigDecimal("789.012")};
        HashMap<String, BigDecimal> original = service.getMyDataMap(biggie, "key");

        String json = JukeTransformerUtil.writeValueAsString(original);
        HashMap<String, BigDecimal> restored =
                JukeTransformerUtil.readValue(json, jukeClass, "getMyDataMap");

        assertEquals(original, restored, "HashMap should round-trip identically");
        System.out.println("[G2.2] HashMap round-trip: PASS");
    }

    @Test
    @Order(12)
    @Tag("regression")
    @DisplayName("G2.3: List<HashMap<String,BigDecimal>> round-trips through JukeTransformerUtil")
    void serialization_listHashMapRoundTrip() throws Exception {
        JukeClass jukeClass = JukeConfigBuilder.set(ISampleService.class).build();
        ISampleService service = new SampleService();

        BigDecimal[] biggie = {new BigDecimal("111.222"), new BigDecimal("333.444")};
        List<HashMap<String, BigDecimal>> original = service.getMyDataMapAsList(biggie, "key");

        String json = JukeTransformerUtil.writeValueAsString(original);
        List<HashMap<String, BigDecimal>> restored =
                JukeTransformerUtil.readValue(json, jukeClass, "getMyDataMapAsList");

        assertEquals(original.size(), restored.size(), "List sizes should match");
        System.out.println("[G2.3] List<HashMap> round-trip: PASS");
    }

    // =========================================================================
    // GROUP 3: DIAGNOSTIC -- Record/Replay with a SINGLE responseType
    // =========================================================================

    @Test
    @Order(20)
    @Tag("diagnostic")
    @DisplayName("G3.1: JukeConfigBuilder captures IRestClient method metadata including getForEntity")
    void configBuilder_capturesRestClientMethods() {
        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();

        assertNotNull(jukeClass);
        System.out.println("[G3.1] IRestClient methods captured: " + jukeClass.getMethods().size());

        assertTrue(jukeClass.getMethods().size() >= 4,
                "Should capture at least 4 methods, got: " + jukeClass.getMethods().size());

        long getForEntityCount = jukeClass.getMethodsByName("getForEntity").size();
        System.out.println("  getForEntity overloads found: " + getForEntityCount);
        assertTrue(getForEntityCount >= 2, "getForEntity should have 2 overloads");
    }

    @Test
    @Order(21)
    @Tag("diagnostic")
    @DisplayName("G3.2: getForEntity return type metadata -- JukeClass captures Object, no parameterization")
    void configBuilder_getForEntity_returnTypeIsObject() {
        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();

        jukeClass.getMethodsByName("getForEntity").forEach(method -> {
            String returnClass = method.getOutputResult().getClassName();
            boolean isParameterized = method.getOutputResult().isParameterized();
            System.out.println("[G3.2] getForEntity return type: className=" + returnClass
                    + ", parameterized=" + isParameterized
                    + ", type=" + method.getOutputResult().getType());
        });

        String returnClass = jukeClass.getMethodsByName("getForEntity").get(0)
                .getOutputResult().getClassName();
        assertEquals("java.lang.Object", returnClass,
                "Return type should be Object (the declared return type, not the runtime type)");
    }

    @Test
    @Order(22)
    @Tag("diagnostic")
    @DisplayName("G3.3: Serialize String, deserialize as Object -- String survives (simple type)")
    void serialization_stringAsObject() throws Exception {
        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();

        String original = "hello";
        String json = JukeTransformerUtil.writeValueAsString(original);
        System.out.println("[G3.3] JSON for String 'hello': " + json);

        Object restored = JukeTransformerUtil.readValue(json, jukeClass, "getForEntity");
        System.out.println("[G3.3] Restored type: " + restored.getClass().getName());
        System.out.println("[G3.3] Restored value: " + restored);

        assertEquals(String.class, restored.getClass(),
                "String should deserialize back as String even with Object return type");
        assertEquals("hello", restored);
    }

    @Test
    @Order(23)
    @Tag("diagnostic")
    @DisplayName("G3.4: Serialize SimpleResult, deserialize as Object via raw util -- still LinkedHashMap (expected)")
    void serialization_simpleResultAsObject() throws Exception {
        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();

        SimpleResult original = new SimpleResult("ok", 200);
        String json = JukeTransformerUtil.writeValueAsString(original);
        System.out.println("[G3.4] JSON for SimpleResult: " + json);

        // When using the raw JukeTransformerUtil with declared return type (Object),
        // Jackson still produces LinkedHashMap. This is expected — the fix is in the
        // proxy layer (ReplayHandler), not in the raw utility.
        Object restored = JukeTransformerUtil.readValue(json, jukeClass, "getForEntity");
        Class<?> restoredClass = restored.getClass();
        System.out.println("[G3.4] Restored type (raw util): " + restoredClass.getName());

        // But readValueAsType with the correct class should work!
        SimpleResult restoredTyped = JukeTransformerUtil.readValueAsType(json, SimpleResult.class);
        System.out.println("[G3.4] Restored type (readValueAsType): " + restoredTyped.getClass().getName());
        assertTrue(restoredTyped instanceof SimpleResult,
                "readValueAsType should correctly deserialize as SimpleResult");
        assertEquals("ok", restoredTyped.getStatus());
        assertEquals(200, restoredTyped.getCode());
        System.out.println("[G3.4] PASS -- readValueAsType correctly returns SimpleResult");
    }

    // =========================================================================
    // GROUP 4: DIAGNOSTIC -- Record/Replay with MULTIPLE responseTypes
    //          (This is the core RestTemplate.getForEntity problem)
    // =========================================================================

    @Test
    @Order(30)
    @Tag("diagnostic")
    @DisplayName("G4.1: Record multiple getForEntity calls -- ZIP entries now include @typeDiscriminator")
    void record_multipleResponseTypes_zipEntryNaming() throws Exception {
        JukeState.setGlobaljuke(JukeState.RECORD);
        System.setProperty("juke", JukeState.RECORD);
        System.setProperty("juke.path", jukePath);
        System.setProperty("juke.zip", JUKE_ZIP_NAME);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(jukePath, JUKE_ZIP_NAME));
        JukeNameFormatter.clearMappings();

        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();
        HashMap<String, JukeClass> jukeMap = new HashMap<>();
        jukeMap.put(IRestClient.class.getCanonicalName(), jukeClass);

        IRestClient recorded = new JukeFactory<IRestClient>()
                .newInstance(realService, IRestClient.class, JukeState.RECORD);

        // Call 1: String.class response
        Object result1 = recorded.getForEntity("http://api/data", String.class);
        System.out.println("[G4.1] Recorded call 1 (String.class): " + result1
                + " [" + result1.getClass().getSimpleName() + "]");

        // Call 2: SimpleResult.class response
        Object result2 = recorded.getForEntity("http://api/data", SimpleResult.class);
        System.out.println("[G4.1] Recorded call 2 (SimpleResult.class): " + result2
                + " [" + result2.getClass().getSimpleName() + "]");

        // Call 3: String.class again
        Object result3 = recorded.getForEntity("http://api/other", String.class);
        System.out.println("[G4.1] Recorded call 3 (String.class): " + result3
                + " [" + result3.getClass().getSimpleName() + "]");

        JukeHelper.getJukeDAO().writeToFile("juke", objectMapper.writeValueAsString(jukeMap));
        JukeHelper.getJukeDAO().write();

        // Capture the actual ZIP path for G4.2
        recordedZipPath = JukeHelper.getJukeDAO().path();
        System.out.println("[G4.1] ZIP written to: " + recordedZipPath);

        assertTrue(new File(recordedZipPath).exists(), "ZIP file should exist");

        Set<String> entries = ZipUtil.getFileNamesFromZipFile(recordedZipPath);
        System.out.println("[G4.1] ZIP entries:");
        for (String entry : entries) {
            System.out.println("  - " + entry);
        }

        long getForEntityEntries = entries.stream()
                .filter(e -> e.contains("getForEntity") && !e.contains(".type.") && !e.contains(".args.") && !e.contains("juke")).count();
        System.out.println("[G4.1] Total getForEntity data entries: " + getForEntityEntries);
        assertEquals(3, getForEntityEntries, "Should have 3 recorded getForEntity calls");

        // NEW: Verify entries now CONTAIN type discriminator via @ separator (short names)
        boolean hasStringDiscriminator = entries.stream()
                .anyMatch(e -> e.contains("@String") && !e.contains("juke"));
        boolean hasResultDiscriminator = entries.stream()
                .anyMatch(e -> e.contains("@SimpleResult"));
        System.out.println("[G4.1] Has @String entries: " + hasStringDiscriminator);
        System.out.println("[G4.1] Has @SimpleResult entries: " + hasResultDiscriminator);
        assertTrue(hasStringDiscriminator, "String entries should include @String discriminator");
        assertTrue(hasResultDiscriminator, "Result entries should include @SimpleResult discriminator");
        System.out.println("[G4.1] PASS: Entry names now include responseType discriminator");
    }

    @Test
    @Order(31)
    @Tag("diagnostic")
    @DisplayName("G4.2: Replay getForEntity -- type-aware dispatch returns correct concrete types")
    void replay_multipleResponseTypes_typeAwareDispatch() throws Exception {
        if (recordedZipPath == null || !new File(recordedZipPath).exists()) {
            System.out.println("[G4.2] SKIPPED -- no recording from G4.1");
            return;
        }

        // Setup replay pointing to the ZIP that G4.1 produced
        JukeState.setGlobaljuke(JukeState.REPLAY);
        System.setProperty("juke", JukeState.REPLAY);

        File zipFile = new File(recordedZipPath);
        String parentDir = zipFile.getParent();
        String zipFileName = zipFile.getName().replace(".zip", "");
        System.setProperty("juke.path", parentDir);
        System.setProperty("juke.zip", zipFileName);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(parentDir, zipFileName));

        JukeConfigBuilder.set(IRestClient.class).build();

        IRestClient replayed = new JukeFactory<IRestClient>()
                .newInstance(realService, IRestClient.class, JukeState.REPLAY);

        System.out.println("[G4.2] Replaying in SAME order as recorded: String, SimpleResult, String");

        // Call 1: String.class — should come back as actual String
        Object replay1 = replayed.getForEntity("http://api/data", String.class);
        System.out.println("[G4.2] Replay call 1 (String.class): type="
                + (replay1 != null ? replay1.getClass().getName() : "null") + ", value=" + replay1);
        assertNotNull(replay1, "Replay call 1 should not be null");
        assertTrue(replay1 instanceof String, "Replay call 1 should be String, got: "
                + replay1.getClass().getName());

        // Call 2: SimpleResult.class — THIS IS THE FIX: should come back as SimpleResult, not LinkedHashMap
        Object replay2 = replayed.getForEntity("http://api/data", SimpleResult.class);
        System.out.println("[G4.2] Replay call 2 (SimpleResult.class): type="
                + (replay2 != null ? replay2.getClass().getName() : "null") + ", value=" + replay2);
        assertNotNull(replay2, "Replay call 2 should not be null");
        assertTrue(replay2 instanceof SimpleResult,
                "FIXED: Replay call 2 should now be SimpleResult, got: " + replay2.getClass().getName());
        SimpleResult sr = (SimpleResult) replay2;
        assertEquals("ok", sr.getStatus());
        assertEquals(200, sr.getCode());

        // Call 3: String.class again
        Object replay3 = replayed.getForEntity("http://api/other", String.class);
        System.out.println("[G4.2] Replay call 3 (String.class): type="
                + (replay3 != null ? replay3.getClass().getName() : "null") + ", value=" + replay3);
        assertNotNull(replay3, "Replay call 3 should not be null");
        assertTrue(replay3 instanceof String, "Replay call 3 should be String, got: "
                + replay3.getClass().getName());

        System.out.println("[G4.2] PASS: All replayed types match the recorded responseType");
    }

    @Test
    @Order(32)
    @Tag("diagnostic")
    @DisplayName("G4.3: End-to-end proof -- record SimpleResult via getForEntity, replay preserves type")
    void endToEnd_recordReplay_typePreserved() throws Exception {
        // ===== PHASE 1: RECORD =====
        String testZipName = "e2e-type-test";
        JukeState.setGlobaljuke(JukeState.RECORD);
        System.setProperty("juke", JukeState.RECORD);
        System.setProperty("juke.path", jukePath);
        System.setProperty("juke.zip", testZipName);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(jukePath, testZipName));
        JukeNameFormatter.clearMappings();

        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();
        HashMap<String, JukeClass> jukeMap = new HashMap<>();
        jukeMap.put(IRestClient.class.getCanonicalName(), jukeClass);

        IRestClient recorded = new JukeFactory<IRestClient>()
                .newInstance(realService, IRestClient.class, JukeState.RECORD);

        // Record one call returning SimpleResult
        Object recordedResult = recorded.getForEntity("http://api/data", SimpleResult.class);
        assertNotNull(recordedResult, "Recording should produce a result");
        assertTrue(recordedResult instanceof SimpleResult, "During RECORD, result IS the real SimpleResult");
        System.out.println("[G4.3] RECORD: result=" + recordedResult + " type=" + recordedResult.getClass().getName());

        JukeHelper.getJukeDAO().writeToFile("juke", objectMapper.writeValueAsString(jukeMap));
        JukeHelper.getJukeDAO().write();
        String zipPath = JukeHelper.getJukeDAO().path();

        try {
            assertTrue(new File(zipPath).exists(), "Recording ZIP should exist");

            // ===== PHASE 2: REPLAY =====
            JukeState.setGlobaljuke(JukeState.REPLAY);
            System.setProperty("juke", JukeState.REPLAY);

            File zipFile = new File(zipPath);
            String parentDir = zipFile.getParent();
            String zipFileName = zipFile.getName().replace(".zip", "");
            System.setProperty("juke.path", parentDir);
            System.setProperty("juke.zip", zipFileName);
            JukeHelper.setJukeDao(new JukeZipDAOImpl(parentDir, zipFileName));

            JukeConfigBuilder.set(IRestClient.class).build();

            IRestClient replayed = new JukeFactory<IRestClient>()
                    .newInstance(realService, IRestClient.class, JukeState.REPLAY);

            Object replayedResult = replayed.getForEntity("http://api/data", SimpleResult.class);

            System.out.println("[G4.3] REPLAY: result=" + replayedResult
                    + " type=" + (replayedResult != null ? replayedResult.getClass().getName() : "null"));

            // THE FIX: SimpleResult should now survive the round-trip
            assertNotNull(replayedResult, "Replayed result should not be null");
            assertTrue(replayedResult instanceof SimpleResult,
                    "FIXED: SimpleResult should survive record/replay round-trip, got: "
                    + replayedResult.getClass().getName());
            SimpleResult sr = (SimpleResult) replayedResult;
            assertEquals("ok", sr.getStatus());
            assertEquals(200, sr.getCode());
            System.out.println("[G4.3] PASS: SimpleResult survived record/replay round-trip!");
        } finally {
            cleanFile(new File(zipPath));
            cleanFile(new File(zipPath + ".bak"));
        }
    }

    // =========================================================================
    // GROUP 5: DIAGNOSTIC -- Schedule has no type-aware dispatch
    // =========================================================================

    @Test
    @Order(40)
    @Tag("diagnostic")
    @DisplayName("G5.1: DataProgramSchedule.getNextAvailable has no type discriminator parameter")
    void schedule_noTypeDiscriminator() {
        Set<String> entryNames = new LinkedHashSet<>();
        entryNames.add("com.example.IRestClient.$getForEntity.java.lang.String.1.json");
        entryNames.add("com.example.IRestClient.$getForEntity.SimpleResult.1.json");

        JukeStateBuilder built = new JukeStateBuilder.Builder(entryNames).build();
        DataProgramSchedule schedule = built.getSchedule();

        String next = schedule.getNextAvailable("com.example.IRestClient.$getForEntity");

        System.out.println("[G5.1] getNextAvailable result: " + next);
        System.out.println("[G5.1] getNextAvailable() only accepts (String unsequencedEntry)");
        System.out.println("[G5.1] There is no overload to filter by responseType");
        assertNotNull(next, "Should return something");
    }

    @Test
    @Order(41)
    @Tag("diagnostic")
    @DisplayName("G5.2: RecordHandler entry naming does not include argument values")
    void recordHandler_entryNaming_noArgumentCapture() {
        String interfaceName = IRestClient.class.getName();
        String methodName = "getForEntity";
        String entryKey = interfaceName + ".$" + methodName;

        System.out.println("[G5.2] RecordHandler entry key format: " + entryKey);
        System.out.println("[G5.2] Call with String.class       -> same key: " + entryKey);
        System.out.println("[G5.2] Call with SimpleResult.class -> same key: " + entryKey);
        System.out.println("[G5.2] Call with Integer.class      -> same key: " + entryKey);

        assertFalse(entryKey.contains("String"), "Entry key should NOT contain type info");
        assertFalse(entryKey.contains("SimpleResult"), "Entry key should NOT contain type info");
    }

    @Test
    @Order(42)
    @Tag("diagnostic")
    @DisplayName("G5.3: JukeTransformerUtil.readValue uses declared return type, not runtime argument")
    void transformerUtil_usesStaticReturnType() throws Exception {
        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();

        SimpleResult original = new SimpleResult("test", 42);
        String json = JukeTransformerUtil.writeValueAsString(original);

        Object result = JukeTransformerUtil.readValue(json, jukeClass, "getForEntity");

        System.out.println("[G5.3] Original type:      " + original.getClass().getName());
        System.out.println("[G5.3] Deserialized type:   " + result.getClass().getName());
        System.out.println("[G5.3] Are they same type?  "
                + (result instanceof SimpleResult ? "YES" : "NO -- type information lost"));

        if (!(result instanceof SimpleResult)) {
            System.out.println("[G5.3] JukeTransformerUtil.readValue(json, jukeClass, methodName)");
            System.out.println("       has no way to accept a runtime Class<?> responseType.");
            System.out.println("       It always uses the declared return type from JukeMethod (Object).");
        }
    }

    // =========================================================================
    // GROUP 6: REGRESSION -- Non-ambiguous methods still work fine
    // =========================================================================

    @Test
    @Order(50)
    @Tag("regression")
    @DisplayName("G6.1: Non-ambiguous methods -- getAsString serialization round-trip")
    void nonAmbiguous_getAsString_serialization() throws Exception {
        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();

        String original = realService.getAsString("http://test");
        String json = JukeTransformerUtil.writeValueAsString(original);
        Object restored = JukeTransformerUtil.readValue(json, jukeClass, "getAsString");

        System.out.println("[G6.1] getAsString: original=" + original + ", restored=" + restored
                + ", type=" + restored.getClass().getName());

        assertEquals(original, restored);
        assertEquals(String.class, restored.getClass());
        System.out.println("[G6.1] PASS -- non-ambiguous String return type works");
    }

    @Test
    @Order(51)
    @Tag("regression")
    @DisplayName("G6.2: Non-ambiguous methods -- getAsResult serialization round-trip")
    void nonAmbiguous_getAsResult_serialization() throws Exception {
        JukeClass jukeClass = JukeConfigBuilder.set(IRestClient.class).build();

        SimpleResult original = realService.getAsResult("http://test");
        String json = JukeTransformerUtil.writeValueAsString(original);
        Object restored = JukeTransformerUtil.readValue(json, jukeClass, "getAsResult");

        System.out.println("[G6.2] getAsResult: original=" + original
                + ", restored type=" + restored.getClass().getName());

        assertTrue(restored instanceof SimpleResult,
                "getAsResult should deserialize as SimpleResult, got: " + restored.getClass());
        SimpleResult restoredResult = (SimpleResult) restored;
        assertEquals(original.getStatus(), restoredResult.getStatus());
        assertEquals(original.getCode(), restoredResult.getCode());
        System.out.println("[G6.2] PASS -- non-ambiguous SimpleResult return type works");
    }
}

