package com.example.controllerdemo;

import com.example.controllerdemo.GreetController.Greeting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code @JukeController} capture + drift detection end to end through
 * the real {@code /service/*} control surface — no browser, runs under
 * {@code mvn test}.
 *
 * <p>Record one call (baseline), then replay the <em>same</em> input (clean —
 * no finding) and a <em>different</em> input (poisoned — a CONTROLLER_MISMATCH
 * is logged). The advice never changes the response, so we assert on the live
 * responses and on the captured log.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18098",
                "juke.enabled=true",
                "juke.zip=controller-it"
        })
@ExtendWith(OutputCaptureExtension.class)
class ControllerCaptureTest {

    static final String TRACK = "controller-it";

    @DynamicPropertySource
    static void uniqueJukePath(DynamicPropertyRegistry r) {
        r.add("juke.path", () -> System.getProperty("java.io.tmpdir")
                + "/juke-controller-it-" + System.nanoTime());
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void cleanReplayMatches_poisonedReplayLogsMismatch(CapturedOutput output) {
        // ── RECORD a baseline (request + response captured at step 1) ─────────
        ok(http.getForEntity("/service/record/start?track=" + TRACK, String.class));
        Greeting recorded = http.getForObject("/api/greet/Alice", Greeting.class);
        ok(http.getForEntity("/service/record/end", String.class));
        assertEquals("Hello, Alice!", recorded.message());

        // ── CLEAN replay: same input → matches baseline → no finding ──────────
        ok(http.getForEntity("/service/replay/start?track=" + TRACK, String.class));
        Greeting clean = http.getForObject("/api/greet/Alice", Greeting.class);
        assertEquals("Hello, Alice!", clean.message());
        assertFalse(output.getAll().contains("CONTROLLER_MISMATCH"),
                "clean replay (same input) must not log a controller mismatch");

        // ── POISONED replay: different input → deviates → mismatch logged ─────
        // replay/start resets the per-class step counter, so this call is step 1
        // again and is diffed against the Alice baseline.
        ok(http.getForEntity("/service/replay/start?track=" + TRACK, String.class));
        Greeting poisoned = http.getForObject("/api/greet/Bob", Greeting.class);
        assertEquals("Hello, Bob!", poisoned.message(),
                "the advice observes/diffs but never changes the live response");
        assertTrue(output.getAll().contains("CONTROLLER_MISMATCH"),
                "poisoned replay (different input) must log a CONTROLLER_MISMATCH");
    }

    private static void ok(ResponseEntity<String> resp) {
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "control-surface call failed: " + resp.getStatusCode() + " " + resp.getBody());
    }
}
