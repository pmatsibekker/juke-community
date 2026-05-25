package com.example.statusgrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the cross-session status view end to end through the real
 * {@code /service/*} control surface — no browser, runs under {@code mvn test}.
 *
 * <p>Record one track of six calls (global record), then start two independent
 * cookie sessions replaying that same track and drive a <em>different</em>
 * number of calls through each. {@code GET /service/sessions} must then report
 * both sessions, each with its own {@code lastCall} and {@code percentComplete}
 * — the data a live multi-worker status grid renders.
 *
 * <p>Each session is identified only by its {@code JUKE_SESSION_ID} cookie, so
 * the test manages two cookie jars by hand: capture the {@code Set-Cookie}
 * headers from {@code /service/session/start} and replay them on each drive
 * call. That is exactly what a browser does per tab.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18096",
                "juke.enabled=true",
                "juke.zip=status-grid-it"
        })
class StatusGridSessionsTest {

    static final String TRACK = "status-grid-it";

    /** Six recorded calls → one seam entry of length 6 in the ZIP. */
    static final List<String> RECORDED = List.of("Ann", "Ben", "Cara", "Dan", "Eve", "Finn");

    @DynamicPropertySource
    static void uniqueJukePath(DynamicPropertyRegistry r) {
        r.add("juke.path", () -> System.getProperty("java.io.tmpdir")
                + "/juke-status-grid-it-" + System.nanoTime());
    }

    @Autowired
    TestRestTemplate http;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void twoSessionsReportDistinctLastCallAndPercentComplete() throws Exception {
        // ── RECORD a six-call baseline track (global record mode) ─────────────
        ok(http.getForEntity("/service/record/start?track=" + TRACK, String.class));
        for (String name : RECORDED) {
            http.getForObject("/api/greet/" + name, String.class);
        }
        ok(http.getForEntity("/service/record/end", String.class));

        // ── SESSION A: replay the track, drive 2 of the 6 calls ───────────────
        String cookieA = startSession("worker-checkout");
        drive(cookieA, 2);

        // ── SESSION B: replay the same track, drive 4 of the 6 calls ──────────
        String cookieB = startSession("worker-search");
        drive(cookieB, 4);

        // ── /service/sessions reports both, each with its own progress ────────
        ResponseEntity<String> resp = http.getForEntity("/service/sessions", String.class);
        ok(resp);
        JsonNode body = mapper.readTree(resp.getBody());

        assertEquals(2, body.path("activeSessionCount").asInt(),
                "two concurrent sessions should be reported; was: " + resp.getBody());

        String sidA = sessionIdOf(cookieA);
        String sidB = sessionIdOf(cookieB);
        JsonNode a = sessionNode(body, sidA);
        JsonNode b = sessionNode(body, sidB);
        assertNotNull(a, "session A missing from /service/sessions: " + resp.getBody());
        assertNotNull(b, "session B missing from /service/sessions: " + resp.getBody());

        // Both are replay sessions of the recorded track.
        assertEquals("replay", a.path("mode").asText());
        assertEquals("replay", b.path("mode").asText());
        assertEquals(TRACK, a.path("track").asText());
        assertEquals(TRACK, b.path("track").asText());

        // lastCall.sequence is the literal call count for the seam, so A (drove
        // 2) is at sequence 2 and B (drove 4) is at sequence 4 — proof the grid
        // tracks each session's cursor independently.
        assertEquals(2, a.path("lastCall").path("sequence").asInt(),
                "session A drove 2 calls; lastCall.sequence should be 2: " + a);
        assertEquals(4, b.path("lastCall").path("sequence").asInt(),
                "session B drove 4 calls; lastCall.sequence should be 4: " + b);
        assertTrue(a.path("lastCall").path("displayName").asText().contains("greet"),
                "lastCall should name the greet seam: " + a);

        // percentComplete advances with the cursor: B (further along) reports a
        // higher percentage than A, and both are strictly partial — the grid is
        // showing two in-progress workers, not finished ones.
        double pctA = a.path("percentComplete").asDouble();
        double pctB = b.path("percentComplete").asDouble();
        assertTrue(pctA > 0.0 && pctA < 100.0,
                "session A should be partway through (0 < pct < 100); was " + pctA);
        assertTrue(pctB > 0.0 && pctB < 100.0,
                "session B should be partway through (0 < pct < 100); was " + pctB);
        assertTrue(pctB > pctA,
                "session B drove more calls, so its percentComplete should exceed A's; A="
                        + pctA + " B=" + pctB);
    }

    /** Starts a cookie session for {@link #TRACK} and returns its Cookie header value. */
    private String startSession(String description) {
        ResponseEntity<String> resp = http.getForEntity(
                "/service/session/start?track=" + TRACK + "&description=" + description, String.class);
        ok(resp);
        List<String> setCookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookies, "session/start did not set cookies");
        StringBuilder cookie = new StringBuilder();
        for (String sc : setCookies) {
            if (cookie.length() > 0) cookie.append("; ");
            cookie.append(sc.split(";", 2)[0]); // keep only name=value
        }
        return cookie.toString();
    }

    /** Drives {@code n} greet calls carrying the given session cookie. */
    private void drive(String cookie, int n) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        HttpEntity<Void> req = new HttpEntity<>(headers);
        for (int i = 0; i < n; i++) {
            ResponseEntity<String> resp = http.exchange(
                    "/api/greet/" + RECORDED.get(i), HttpMethod.GET, req, String.class);
            ok(resp);
        }
    }

    private static String sessionIdOf(String cookie) {
        for (String part : cookie.split(";\\s*")) {
            if (part.startsWith("JUKE_SESSION_ID=")) {
                return part.substring("JUKE_SESSION_ID=".length());
            }
        }
        throw new AssertionError("no JUKE_SESSION_ID in cookie: " + cookie);
    }

    private static JsonNode sessionNode(JsonNode body, String sessionId) {
        for (JsonNode s : body.path("sessions")) {
            if (sessionId.equals(s.path("sessionId").asText())) {
                return s;
            }
        }
        return null;
    }

    private static void ok(ResponseEntity<String> resp) {
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "control-surface call failed: " + resp.getStatusCode() + " " + resp.getBody());
    }
}
