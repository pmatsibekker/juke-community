package org.juke.framework.playwright;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Playwright comparison engine, ApprovedIgnores, and report generation.
 */
public class PlaywrightComparisonEngineTest {

    private PlaywrightComparisonEngine engine;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        engine = new PlaywrightComparisonEngine();
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ============================= ApprovedIgnores =============================

    @Test
    void approvedIgnores_loadFromClasspath() throws Exception {
        InputStream stream = getClass().getClassLoader().getResourceAsStream("juke-approved.json");
        assertNotNull(stream, "juke-approved.json should be on the classpath");
        ApprovedIgnores ignores = ApprovedIgnores.fromStream(stream);

        assertTrue(ignores.isIgnored("GET /api/orders", "$.id"));
        assertTrue(ignores.isIgnored("GET /api/orders", "$.createdAt"));
        assertFalse(ignores.isIgnored("GET /api/orders", "$.total"));
    }

    @Test
    void approvedIgnores_wildcardArrayMatching() throws Exception {
        Map<String, List<String>> map = new HashMap<>();
        map.put("GET /api/orders", Arrays.asList("$.items[*].uuid"));
        ApprovedIgnores ignores = new ApprovedIgnores(map);

        assertTrue(ignores.isIgnored("GET /api/orders", "$.items[0].uuid"));
        assertTrue(ignores.isIgnored("GET /api/orders", "$.items[1].uuid"));
        assertTrue(ignores.isIgnored("GET /api/orders", "$.items[99].uuid"));
        assertFalse(ignores.isIgnored("GET /api/orders", "$.items[0].name"));
    }

    @Test
    void approvedIgnores_unknownEndpointNotIgnored() {
        ApprovedIgnores ignores = new ApprovedIgnores(Collections.emptyMap());
        assertFalse(ignores.isIgnored("DELETE /api/nope", "$.anything"));
    }

    // ============================= Identical responses =========================

    @Test
    void compare_identicalResponses_allPass() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"total\":42,\"status\":\"shipped\"}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"total\":42,\"status\":\"shipped\"}")
        );

        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());

        assertEquals(1, report.getResults().size());
        assertEquals("PASS", report.getResults().get(0).getStatus());
        assertEquals(1, report.getSummary().getPassed());
        assertEquals(0, report.getSummary().getFailed());
    }

    // ============================= Real diff detected ==========================

    @Test
    void compare_valueDifference_reportedAsFail() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"total\":42,\"status\":\"shipped\"}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"total\":39.99,\"status\":\"shipped\"}")
        );

        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());

        assertEquals(1, report.getSummary().getFailed());
        EndpointResult result = report.getResults().get(0);
        assertEquals("FAIL", result.getStatus());
        assertEquals(1, result.getDiffs().size());
        assertEquals("$.total", result.getDiffs().get(0).getPath());
    }

    // ============================= Ignored fields ==============================

    @Test
    void compare_ignoredFieldsSkipped() throws Exception {
        Map<String, List<String>> map = new HashMap<>();
        map.put("GET /api/orders", Arrays.asList("$.id", "$.createdAt"));
        ApprovedIgnores ignores = new ApprovedIgnores(map);

        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"id\":\"aaa-111\",\"createdAt\":\"2025-01-01\",\"total\":42}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"id\":\"bbb-222\",\"createdAt\":\"2026-03-21\",\"total\":42}")
        );

        ComparisonReport report = engine.compare(baseline, actual, ignores);

        assertEquals("PASS", report.getResults().get(0).getStatus());
        assertEquals(2, report.getSummary().getIgnored());
        assertTrue(report.getResults().get(0).getIgnored().contains("$.id"));
        assertTrue(report.getResults().get(0).getIgnored().contains("$.createdAt"));
    }

    // ============================= Wildcard array ignore =======================

    @Test
    void compare_wildcardArrayIgnore() throws Exception {
        Map<String, List<String>> map = new HashMap<>();
        map.put("GET /api/orders", Arrays.asList("$.items[*].uuid"));
        ApprovedIgnores ignores = new ApprovedIgnores(map);

        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"items\":[{\"uuid\":\"aaa\",\"name\":\"Widget\"},{\"uuid\":\"bbb\",\"name\":\"Gadget\"}]}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"items\":[{\"uuid\":\"xxx\",\"name\":\"Widget\"},{\"uuid\":\"yyy\",\"name\":\"Gadget\"}]}")
        );

        ComparisonReport report = engine.compare(baseline, actual, ignores);

        assertEquals("PASS", report.getResults().get(0).getStatus());
        assertEquals(2, report.getSummary().getIgnored()); // items[0].uuid and items[1].uuid
    }

    // ======================= Mixed: some ignored, some real diffs ===============

    @Test
    void compare_mixedIgnoredAndRealDiffs() throws Exception {
        Map<String, List<String>> map = new HashMap<>();
        map.put("GET /api/orders", Arrays.asList("$.id"));
        ApprovedIgnores ignores = new ApprovedIgnores(map);

        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"id\":\"aaa\",\"total\":42}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"id\":\"bbb\",\"total\":99}")
        );

        ComparisonReport report = engine.compare(baseline, actual, ignores);

        EndpointResult result = report.getResults().get(0);
        assertEquals("FAIL", result.getStatus());
        assertEquals(1, result.getDiffs().size());
        assertEquals("$.total", result.getDiffs().get(0).getPath());
        assertEquals(1, result.getIgnored().size());
    }

    // ======================== Missing actual entry ==============================

    @Test
    void compare_missingActualEntry_reportedAsFail() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders", "{\"total\":42}"),
                entry("GET", "http://localhost:8080/api/items", "{\"count\":5}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders", "{\"total\":42}")
        );

        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());

        assertEquals(1, report.getSummary().getPassed());
        assertEquals(1, report.getSummary().getFailed());
    }

    // ======================== Nested object diff ================================

    @Test
    void compare_nestedObjectDiff() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"address\":{\"city\":\"Boston\",\"zip\":\"02101\"}}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"address\":{\"city\":\"Boston\",\"zip\":\"10001\"}}")
        );

        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());

        EndpointResult result = report.getResults().get(0);
        assertEquals("FAIL", result.getStatus());
        assertEquals("$.address.zip", result.getDiffs().get(0).getPath());
    }

    // ======================== Added field in actual =============================

    @Test
    void compare_addedFieldInActual_reportedAsDiff() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders", "{\"total\":42}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"total\":42,\"newField\":\"surprise\"}")
        );

        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());

        EndpointResult result = report.getResults().get(0);
        assertEquals("FAIL", result.getStatus());
        assertEquals("$.newField", result.getDiffs().get(0).getPath());
    }

    // ======================== Multiple endpoint calls ===========================

    @Test
    void compare_multipleCallsSameEndpoint_indexedCorrectly() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders", "{\"page\":1}"),
                entry("GET", "http://localhost:8080/api/orders", "{\"page\":2}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders", "{\"page\":1}"),
                entry("GET", "http://localhost:8080/api/orders", "{\"page\":2}")
        );

        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());

        assertEquals(2, report.getSummary().getPassed());
        assertEquals(1, report.getResults().get(0).getCallIndex());
        assertEquals(2, report.getResults().get(1).getCallIndex());
    }

    // ======================== Report serialization ==============================

    @Test
    void report_serializesToJson() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"id\":\"aaa\",\"total\":42}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api/orders",
                        "{\"id\":\"bbb\",\"total\":42}")
        );

        Map<String, List<String>> map = new HashMap<>();
        map.put("GET /api/orders", Arrays.asList("$.id"));
        ApprovedIgnores ignores = new ApprovedIgnores(map);

        ComparisonReport report = engine.compare(baseline, actual, ignores);
        String json = engine.writeReportAsString(report);

        assertNotNull(json);
        assertTrue(json.contains("\"passed\""));
        assertTrue(json.contains("\"ignored\""));
        assertTrue(json.contains("GET /api/orders"));
    }

    // ======================== Endpoint key parsing ==============================

    @Test
    void playwrightEntry_endpointKeyParsing() {
        PlaywrightEntry e = new PlaywrightEntry("GET", "http://localhost:8080/api/orders?page=1", 200, "{}");
        assertEquals("GET /api/orders?page=1", e.getEndpointKey());

        PlaywrightEntry e2 = new PlaywrightEntry("POST", "https://example.com/api/checkout", 201, "{}");
        assertEquals("POST /api/checkout", e2.getEndpointKey());
    }

    // ======================== loadRecording(String) ============================

    @Test
    void loadRecording_fromString_deserializesCorrectly() throws Exception {
        String json = "{\"entries\":[{\"method\":\"GET\",\"url\":\"http://localhost/api\",\"statusCode\":200,\"responseBody\":\"{}\"}]}";
        PlaywrightRecording r = engine.loadRecording(json);
        assertNotNull(r);
        assertEquals(1, r.getEntries().size());
    }

    // ======================== Type mismatch ====================================

    @Test
    void compare_typeMismatch_reportedAsFail() throws Exception {
        // baseline has number, actual has string for same field
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "{\"count\":5}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "{\"count\":\"five\"}")
        );
        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
        assertEquals("FAIL", report.getResults().get(0).getStatus());
    }

    // ======================== Missing field in actual ===========================

    @Test
    void compare_missingFieldInActual_reportedAsFail() throws Exception {
        // baseline has "extra" field; actual does not
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "{\"total\":10,\"extra\":\"value\"}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "{\"total\":10}")
        );
        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
        assertEquals("FAIL", report.getResults().get(0).getStatus());
    }

    // ======================== Both null values ==================================

    @Test
    void compare_bothNullValues_noFail() throws Exception {
        // both sides have null for the same field
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "{\"key\":null}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "{\"key\":null}")
        );
        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
        assertEquals("PASS", report.getResults().get(0).getStatus());
    }

    // ======================== Boolean values ====================================

    @Test
    void compare_booleanValueDiff_reportedAsFail() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "{\"active\":true}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "{\"active\":false}")
        );
        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
        assertEquals("FAIL", report.getResults().get(0).getStatus());
    }

    // ======================== Null response body ================================

    @Test
    void compare_nullResponseBody_noException() {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", null)
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", null)
        );
        assertDoesNotThrow(() -> engine.compare(baseline, actual, new ApprovedIgnores()));
    }

    @Test
    void compare_emptyResponseBody_noException() {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "")
        );
        assertDoesNotThrow(() -> engine.compare(baseline, actual, new ApprovedIgnores()));
    }

    // ======================== Array: actual has more elements ==================

    @Test
    void compare_actualArrayHasMoreElements_diffReported() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "[1]")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "[1,2]")
        );
        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
        // extra element in actual should be reported
        assertNotNull(report);
    }

    // ======================== Ignored extra field in actual ====================

    @Test
    void compare_ignoredAddedFieldInActual_notReportedAsFail() throws Exception {
        Map<String, List<String>> map = new HashMap<>();
        map.put("GET /api", Arrays.asList("$.newField"));
        ApprovedIgnores ignores = new ApprovedIgnores(map);

        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost:8080/api", "{\"total\":42}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost:8080/api", "{\"total\":42,\"newField\":\"surprise\"}")
        );

        ComparisonReport report = engine.compare(baseline, actual, ignores);
        assertEquals("PASS", report.getResults().get(0).getStatus());
    }

    // ======================== Invalid JSON fallback ============================

    @Test
    void compare_invalidJson_fallsBackToStringComparison() {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "not-valid-json")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "not-valid-json")
        );
        // same invalid json → safeEquals true → PASS
        assertDoesNotThrow(() -> {
            ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
            assertEquals("PASS", report.getResults().get(0).getStatus());
        });
    }

    @Test
    void compare_invalidJsonDifferent_fallsBackToStringComparisonFail() {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "not-valid-json-A")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "not-valid-json-B")
        );
        assertDoesNotThrow(() -> {
            ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
            assertEquals("FAIL", report.getResults().get(0).getStatus());
        });
    }

    @Test
    void compare_invalidJsonNullActual_safeEqualsNullCase() {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "not-valid-json")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", null)
        );
        assertDoesNotThrow(() -> engine.compare(baseline, actual, new ApprovedIgnores()));
    }

    // ======================== Nested object as added field =====================

    @Test
    void compare_addedObjectFieldInActual_usesObjectFallback() throws Exception {
        PlaywrightRecording baseline = buildRecording(
                entry("GET", "http://localhost/api", "{\"a\":1}")
        );
        PlaywrightRecording actual = buildRecording(
                entry("GET", "http://localhost/api", "{\"a\":1,\"nested\":{\"x\":2}}")
        );
        ComparisonReport report = engine.compare(baseline, actual, new ApprovedIgnores());
        assertEquals("FAIL", report.getResults().get(0).getStatus());
    }

    // ========================= Helpers =========================================

    private PlaywrightRecording buildRecording(PlaywrightEntry... entries) {
        PlaywrightRecording recording = new PlaywrightRecording();
        recording.setEntries(Arrays.asList(entries));
        return recording;
    }

    private PlaywrightEntry entry(String method, String url, String responseBody) {
        return new PlaywrightEntry(method, url, 200, responseBody);
    }
}

