package org.juke.framework.playwright;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the playwright model POJOs:
 * {@link JsonDiff}, {@link EndpointResult}, {@link PlaywrightEntry},
 * {@link PlaywrightRecording}, {@link ComparisonReport}.
 */
class PlaywrightModelTest {

    // ── JsonDiff ──────────────────────────────────────────────────────────

    @Test
    void jsonDiff_defaultConstructor_andSetters() {
        JsonDiff diff = new JsonDiff();
        diff.setPath("$.name");
        diff.setExpected("Alice");
        diff.setActual("Bob");

        assertEquals("$.name", diff.getPath());
        assertEquals("Alice", diff.getExpected());
        assertEquals("Bob", diff.getActual());
    }

    @Test
    void jsonDiff_allArgsConstructor() {
        JsonDiff diff = new JsonDiff("$.age", 30, 31);
        assertEquals("$.age", diff.getPath());
        assertEquals(30, diff.getExpected());
        assertEquals(31, diff.getActual());
    }

    @Test
    void jsonDiff_toString_containsFields() {
        JsonDiff diff = new JsonDiff("$.x", "a", "b");
        String s = diff.toString();
        assertTrue(s.contains("$.x"));
        assertTrue(s.contains("a"));
        assertTrue(s.contains("b"));
    }

    // ── EndpointResult ────────────────────────────────────────────────────

    @Test
    void endpointResult_defaultConstructor_andSetters() {
        EndpointResult er = new EndpointResult();
        er.setEndpoint("/api/test");
        er.setCallIndex(2);
        er.setStatus("PASS");
        er.setDiffs(List.of());
        er.setIgnored(List.of());

        assertEquals("/api/test", er.getEndpoint());
        assertEquals(2, er.getCallIndex());
        assertEquals("PASS", er.getStatus());
        assertTrue(er.getDiffs().isEmpty());
        assertTrue(er.getIgnored().isEmpty());
    }

    @Test
    void endpointResult_allArgsConstructor_statusIsPass() {
        EndpointResult er = new EndpointResult("/api/orders", 1);
        assertEquals("/api/orders", er.getEndpoint());
        assertEquals(1, er.getCallIndex());
        assertEquals("PASS", er.getStatus());
    }

    @Test
    void endpointResult_addDiff_setsStatusToFail() {
        EndpointResult er = new EndpointResult("/api/x", 0);
        assertEquals("PASS", er.getStatus());
        er.addDiff(new JsonDiff("$.a", 1, 2));
        assertEquals("FAIL", er.getStatus());
        assertEquals(1, er.getDiffs().size());
    }

    @Test
    void endpointResult_addIgnored_appendsPath() {
        EndpointResult er = new EndpointResult("/api/x", 0);
        er.addIgnored("$.timestamp");
        assertEquals(1, er.getIgnored().size());
        assertEquals("$.timestamp", er.getIgnored().get(0));
    }

    // ── PlaywrightEntry ───────────────────────────────────────────────────

    @Test
    void playwrightEntry_defaultConstructor_andSetters() {
        PlaywrightEntry e = new PlaywrightEntry();
        e.setMethod("GET");
        e.setUrl("http://localhost:8080/api/hello");
        e.setStatusCode(200);
        e.setResponseBody("{\"msg\":\"hi\"}");

        assertEquals("GET", e.getMethod());
        assertEquals("http://localhost:8080/api/hello", e.getUrl());
        assertEquals(200, e.getStatusCode());
        assertEquals("{\"msg\":\"hi\"}", e.getResponseBody());
    }

    @Test
    void playwrightEntry_allArgsConstructor() {
        PlaywrightEntry e = new PlaywrightEntry("POST", "http://host/api/create", 201, "{}");
        assertEquals("POST", e.getMethod());
        assertEquals(201, e.getStatusCode());
    }

    @Test
    void playwrightEntry_getEndpointKey_stripsSchemeAndHost() {
        PlaywrightEntry e = new PlaywrightEntry("GET", "https://example.com/api/orders?page=1", 200, "[]");
        assertEquals("GET /api/orders?page=1", e.getEndpointKey());
    }

    @Test
    void playwrightEntry_getEndpointKey_noScheme_returnsMethodAndPath() {
        PlaywrightEntry e = new PlaywrightEntry("DELETE", "/api/item/5", 204, "");
        assertEquals("DELETE /api/item/5", e.getEndpointKey());
    }

    @Test
    void playwrightEntry_getEndpointKey_nullMethodAndUrl_returnsEmpty() {
        PlaywrightEntry e = new PlaywrightEntry();
        assertEquals("", e.getEndpointKey());
    }

    @Test
    void playwrightEntry_getEndpointKey_urlWithoutPath_returnsSlash() {
        // scheme://host with no trailing slash path
        PlaywrightEntry e = new PlaywrightEntry("GET", "https://example.com", 200, "");
        // indexOf('/', after "://") will be -1 → "/" fallback
        String key = e.getEndpointKey();
        assertEquals("GET /", key);
    }

    // ── PlaywrightRecording ───────────────────────────────────────────────

    @Test
    void playwrightRecording_settersAndGetters() {
        PlaywrightRecording rec = new PlaywrightRecording();
        rec.setName("my-recording");
        PlaywrightEntry entry = new PlaywrightEntry("GET", "http://h/a", 200, "{}");
        rec.setEntries(List.of(entry));

        assertEquals("my-recording", rec.getName());
        assertEquals(1, rec.getEntries().size());
    }

    // ── ComparisonReport ──────────────────────────────────────────────────

    @Test
    void comparisonReport_addResult_passUpdatesPassedCount() {
        ComparisonReport report = new ComparisonReport();
        EndpointResult pass = new EndpointResult("/a", 0);

        report.addResult(pass);

        assertEquals(1, report.getSummary().getPassed());
        assertEquals(0, report.getSummary().getFailed());
        assertEquals(0, report.getSummary().getIgnored());
    }

    @Test
    void comparisonReport_addResult_failUpdateFailedCount() {
        ComparisonReport report = new ComparisonReport();
        EndpointResult fail = new EndpointResult("/b", 0);
        fail.addDiff(new JsonDiff("$.x", 1, 2));

        report.addResult(fail);

        assertEquals(0, report.getSummary().getPassed());
        assertEquals(1, report.getSummary().getFailed());
    }

    @Test
    void comparisonReport_addResult_ignoredCountAccumulates() {
        ComparisonReport report = new ComparisonReport();
        EndpointResult er = new EndpointResult("/c", 0);
        er.addIgnored("$.ts");
        er.addIgnored("$.id");

        report.addResult(er);

        assertEquals(2, report.getSummary().getIgnored());
    }

    @Test
    void comparisonReport_summary_settersWork() {
        ComparisonReport.Summary s = new ComparisonReport.Summary();
        s.setPassed(3);
        s.setFailed(1);
        s.setIgnored(5);

        assertEquals(3, s.getPassed());
        assertEquals(1, s.getFailed());
        assertEquals(5, s.getIgnored());
    }

    @Test
    void comparisonReport_setResultsAndSummary() {
        ComparisonReport report = new ComparisonReport();
        report.setResults(List.of(new EndpointResult("/x", 0)));
        ComparisonReport.Summary s = new ComparisonReport.Summary();
        s.setPassed(99);
        report.setSummary(s);

        assertEquals(1, report.getResults().size());
        assertEquals(99, report.getSummary().getPassed());
    }
}

