package com.example.restclient;

import com.example.restclient.Quotes.Quote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the concrete-field {@code @Juke} behavior end to end, through the real
 * {@code /service/*} control surface — no browser, runs under {@code mvn test}.
 *
 * <p>The upstream is the app itself (fixed port), so a live call returns a fresh
 * random {@code quoteId}; record then replay must return the <em>recorded</em>
 * ids, which is only possible if Juke actually wrapped and replayed the
 * concrete {@code RestTemplate} seams.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "server.port=18099",
                "sample.upstream.base-url=http://localhost:18099",
                "juke.enabled=true",
                "juke.zip=rest-client-it"
        })
class RestClientRecordReplayTest {

    static final String TRACK = "rest-client-it";

    @DynamicPropertySource
    static void uniqueJukePath(DynamicPropertyRegistry r) {
        // Unique recording dir per run so a stale/locked ZIP can never bleed in
        // (test-isolation discipline).
        r.add("juke.path", () -> System.getProperty("java.io.tmpdir")
                + "/juke-rest-client-it-" + System.nanoTime());
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void recordsThenReplaysBothSeams_disambiguatesByName_excludesHealthCheck() {
        // ── RECORD ──────────────────────────────────────────────────────────
        ok(http.getForEntity("/service/record/start?track=" + TRACK, String.class));
        Quote live = http.getForObject("/api/quote/SKU-1", Quote.class);
        ok(http.getForEntity("/service/record/end", String.class));

        assertNotNull(live);
        assertNotNull(live.shipping().quoteId());
        assertNotNull(live.pricing().quoteId());

        // ── REPLAY (global mode — concrete path is not session-aware) ────────
        ok(http.getForEntity("/service/replay/start?track=" + TRACK, String.class));
        Quote replayed = http.getForObject("/api/quote/SKU-1", Quote.class);

        // The upstream would have produced new random ids; replay returns the
        // recorded ones, proving both concrete seams replayed.
        assertEquals(live.shipping().quoteId(), replayed.shipping().quoteId(),
                "shipping seam should replay the recorded quoteId");
        assertEquals(live.pricing().quoteId(), replayed.pricing().quoteId(),
                "pricing seam should replay the recorded quoteId");

        // ── name disambiguation + excludeMethods ─────────────────────────────
        String inputs = http.getForObject("/service/recording/inputs?track=" + TRACK, String.class);
        assertNotNull(inputs);
        assertTrue(inputs.contains("shipping.getForObject"),
                "recording should key the shipping seam by name; was: " + inputs);
        assertTrue(inputs.contains("pricing.getForObject"),
                "recording should key the pricing seam by name; was: " + inputs);
        assertFalse(inputs.contains("headForHeaders"),
                "excludeMethods should keep the health-check out of the recording; was: " + inputs);
    }

    private static void ok(ResponseEntity<String> resp) {
        assertTrue(resp.getStatusCode().is2xxSuccessful(),
                "control-surface call failed: " + resp.getStatusCode() + " " + resp.getBody());
    }
}
