package org.juke.remix.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.juke.plugin.api.PluginCapability;
import org.juke.plugin.api.PluginStatus;
import org.juke.plugin.api.registration.CapabilityDescriptor;
import org.juke.plugin.api.registration.Heartbeat;
import org.juke.plugin.api.registration.PluginRegistration;
import org.juke.plugin.api.registration.PluginRegistrationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4.7 acceptance — exercises §8.8 over real HTTP via Spring Boot's {@code RANDOM_PORT}
 * web environment. Hits the controller end-to-end (register → heartbeat → list / capabilities
 * / detail → reaper marks OFFLINE → re-registration rotates token).
 *
 * <p>The plan's bullet 5 lays out three acceptance steps; each gets one {@code @Test}:
 * <ol>
 *   <li>{@link #scenarioA_registerHeartbeatAndDiscover} — a "trivial test plugin" registers
 *       with a {@code USE_CASE_SUGGESTION} capability, the registry response includes a
 *       {@code pluginToken}, the plugin can heartbeat, and {@code GET /service/plugins/capabilities}
 *       lists it.</li>
 *   <li>{@link #scenarioB_reaperMarksOfflineWhenHeartbeatsStop} — drive the reaper directly
 *       (the {@code @Scheduled} loop is disabled in the test profile so we don't sit and
 *       wait 60 seconds in CI). Verify the plugin disappears from
 *       {@code /service/plugins/capabilities}.</li>
 *   <li>{@link #scenarioC_reregistrationRotatesTokenAndRejectsOldOne} — re-register the same
 *       {@code pluginId}; old token returns 401, new token is accepted.</li>
 * </ol>
 *
 * <p>The reaper bean is autowired and driven via {@link PluginHeartbeatReaper#tickOnce()}
 * with the staleness threshold dropped to {@code 1s} via test properties — that lets us
 * assert the reaper logic without sleeping for 60s. The corresponding production threshold
 * (60s) is exercised by {@link PluginRegistryTest} at the unit level using a fixed clock.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "juke.plugins.staleness-seconds=1",
                "juke.plugins.reaper.enabled=true",
                "juke.plugins.reaper.poll-interval-ms=600000",
                "juke.headless-runner.enabled=false"
        })
@ActiveProfiles("test")
class PluginRegistryControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PluginRegistry registry;

    @Autowired
    private PluginHeartbeatReaper reaper;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void resetRegistry() {
        registry.resetForTests();
    }

    @Test
    void scenarioA_registerHeartbeatAndDiscover() {
        // ── Register ─────────────────────────────────────────────────────
        PluginRegistration registration = sampleRegistration("trivial-test-plugin",
                PluginCapability.USE_CASE_SUGGESTION);

        ResponseEntity<PluginRegistrationResponse> registered = http.postForEntity(
                base() + "/service/plugins/register", registration, PluginRegistrationResponse.class);

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PluginRegistrationResponse body = registered.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getPluginToken()).as("token must be issued").isNotBlank();
        assertThat(body.getRegistrationId()).isNotBlank();

        // ── Heartbeat ────────────────────────────────────────────────────
        Heartbeat heartbeat = new Heartbeat(body.getPluginToken(), "ACTIVE");
        ResponseEntity<Void> beat = http.exchange(
                base() + "/service/plugins/trivial-test-plugin/heartbeat",
                HttpMethod.POST,
                jsonEntity(heartbeat),
                Void.class);
        assertThat(beat.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // ── List + capabilities ──────────────────────────────────────────
        ResponseEntity<String> list = http.getForEntity(base() + "/service/plugins", String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains("trivial-test-plugin");

        ResponseEntity<String> caps = http.getForEntity(
                base() + "/service/plugins/capabilities", String.class);
        assertThat(caps.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(caps.getBody())
                .as("USE_CASE_SUGGESTION must list the plugin")
                .contains("USE_CASE_SUGGESTION")
                .contains("trivial-test-plugin");

        // ── Detail page ───────────────────────────────────────────────────
        ResponseEntity<String> detail = http.getForEntity(
                base() + "/service/plugins/trivial-test-plugin", String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).contains("ACTIVE");
    }

    @Test
    void scenarioB_reaperMarksOfflineWhenHeartbeatsStop() throws Exception {
        PluginRegistration registration = sampleRegistration("hibernating-plugin",
                PluginCapability.SCAFFOLD_GENERATION);
        ResponseEntity<PluginRegistrationResponse> registered = http.postForEntity(
                base() + "/service/plugins/register", registration, PluginRegistrationResponse.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Wait one second so we exceed the 1-second staleness threshold the test profile uses.
        Thread.sleep(1100);

        List<String> went = reaper.tickOnce();
        assertThat(went)
                .as("reaper should have flipped the plugin OFFLINE")
                .contains("hibernating-plugin");

        // GET /service/plugins/capabilities no longer lists it.
        ResponseEntity<String> caps = http.getForEntity(
                base() + "/service/plugins/capabilities", String.class);
        JsonNode tree = json.readTree(caps.getBody());
        JsonNode scaffoldList = tree.path("capabilities").path("SCAFFOLD_GENERATION");
        assertThat(scaffoldList.isMissingNode() || scaffoldList.isEmpty())
                .as("OFFLINE plugin must drop out of the capability index")
                .isTrue();

        // GET /service/plugins/{id} still returns it (status OFFLINE) for diagnostics.
        ResponseEntity<String> detail = http.getForEntity(
                base() + "/service/plugins/hibernating-plugin", String.class);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(detail.getBody()).contains(PluginStatus.OFFLINE.name());
    }

    @Test
    void scenarioC_reregistrationRotatesTokenAndRejectsOldOne() {
        PluginRegistration first = sampleRegistration("rotating-plugin",
                PluginCapability.RECORDING_TRANSFORMER);
        ResponseEntity<PluginRegistrationResponse> a = http.postForEntity(
                base() + "/service/plugins/register", first, PluginRegistrationResponse.class);
        String firstToken = a.getBody().getPluginToken();

        // Re-register the same id — should yield a fresh token.
        ResponseEntity<PluginRegistrationResponse> b = http.postForEntity(
                base() + "/service/plugins/register", first, PluginRegistrationResponse.class);
        String secondToken = b.getBody().getPluginToken();
        assertThat(secondToken).isNotEqualTo(firstToken);

        // Heartbeat with the OLD token — must be 401.
        ResponseEntity<String> stale = http.exchange(
                base() + "/service/plugins/rotating-plugin/heartbeat",
                HttpMethod.POST,
                jsonEntity(new Heartbeat(firstToken, "ACTIVE")),
                String.class);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(stale.getBody()).contains("stale_token");

        // Heartbeat with the NEW token — must be 204.
        ResponseEntity<Void> fresh = http.exchange(
                base() + "/service/plugins/rotating-plugin/heartbeat",
                HttpMethod.POST,
                jsonEntity(new Heartbeat(secondToken, "ACTIVE")),
                Void.class);
        assertThat(fresh.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void deregisterRemovesEntryAndReturns404OnRepeat() {
        PluginRegistration reg = sampleRegistration("ephemeral", PluginCapability.UI_HARNESS);
        http.postForEntity(base() + "/service/plugins/register", reg, PluginRegistrationResponse.class);

        ResponseEntity<Void> first = http.exchange(
                base() + "/service/plugins/ephemeral/deregister",
                HttpMethod.POST, HttpEntity.EMPTY, Void.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> second = http.exchange(
                base() + "/service/plugins/ephemeral/deregister",
                HttpMethod.POST, HttpEntity.EMPTY, Void.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void heartbeatForUnknownPluginReturns404WithStableErrorCode() {
        ResponseEntity<String> resp = http.exchange(
                base() + "/service/plugins/never-registered/heartbeat",
                HttpMethod.POST,
                jsonEntity(new Heartbeat("does-not-matter", "ACTIVE")),
                String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).contains("unknown_plugin");
    }

    @Test
    void registrationWithoutCapabilitiesReturns400() {
        PluginRegistration r = new PluginRegistration();
        r.setPluginId("invalid");
        r.setDisplayName("invalid");
        r.setVersion("0.0.1");
        r.setBaseUrl("http://localhost:65000");
        r.setCapabilities(List.of()); // empty

        ResponseEntity<String> resp = http.postForEntity(
                base() + "/service/plugins/register", r, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("invalid_registration");
    }

    // ============================================================ helpers

    private String base() {
        return "http://localhost:" + port;
    }

    private static <T> HttpEntity<T> jsonEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private static PluginRegistration sampleRegistration(String pluginId, PluginCapability capability) {
        PluginRegistration r = new PluginRegistration();
        r.setPluginId(pluginId);
        r.setDisplayName(pluginId + " (test)");
        r.setVersion("0.0.1");
        r.setBaseUrl("http://localhost:65000");
        r.setHealthCheckPath("/actuator/health");
        r.setExpectedHeartbeatIntervalSeconds(15);
        r.setSharedSecret("test-shared-secret");

        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("primaryActionLabel", "Run " + pluginId);
        r.setUiHints(hints);

        CapabilityDescriptor descriptor = new CapabilityDescriptor(capability);
        r.setCapabilities(List.of(descriptor));
        return r;
    }

    /** Forces the test compiler to keep the otherwise-unused TypeReference import as documentation. */
    @SuppressWarnings("unused")
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
}
