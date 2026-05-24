package org.juke.remix.plugin;

import org.juke.plugin.api.error.PluginErrorResponse;
import org.juke.plugin.api.registration.Heartbeat;
import org.juke.plugin.api.registration.PluginRegistration;
import org.juke.plugin.api.registration.PluginRegistrationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * §8.8 — plugin registry & capability discovery. One controller hosting both the
 * plugin-facing registration / heartbeat / deregister endpoints and the admin-ui-facing
 * discovery endpoints, since they all consult exactly one collaborator
 * ({@link PluginRegistry}) and the §8.8 surface is small.
 *
 * <p>Error mapping follows the convention used in {@code RecordingBundleController}: validation
 * failures and missing entities yield a {@link PluginErrorResponse} carrying a stable
 * {@code error} code. {@code unknown_plugin} is 404, {@code stale_token} is 401.
 */
@RestController
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
@RequestMapping("/service/plugins")
public class PluginRegistryController {

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistryController.class);

    private final PluginRegistry registry;
    private final PluginCallLog callLog;
    private final Clock clock;
    private final RestTemplate http;

    @Autowired
    public PluginRegistryController(PluginRegistry registry, PluginCallLog callLog) {
        this(registry, callLog, Clock.systemUTC(), new RestTemplate());
    }

    /** Visible for tests — fixed clock + injectable RestTemplate. */
    public PluginRegistryController(PluginRegistry registry,
                                    PluginCallLog callLog,
                                    Clock clock,
                                    RestTemplate http) {
        this.registry = registry;
        this.callLog = callLog;
        this.clock = clock;
        this.http = http;
    }

    // ============================================================== plugin-facing

    @PostMapping(value = "/register",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PluginRegistrationResponse> register(@RequestBody PluginRegistration body) {
        RegisteredPlugin p = registry.register(body);
        PluginRegistrationResponse resp = new PluginRegistrationResponse();
        resp.setPluginId(p.getPluginId());
        resp.setRegistrationId(p.getRegistrationId());
        resp.setPluginToken(p.getToken());
        resp.setRegisteredAt(p.getRegisteredAt());
        resp.setRecommendedHeartbeatIntervalSeconds(15);
        resp.setRemixSharedSecret(p.getRemixSharedSecret());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping(value = "/{pluginId}/heartbeat",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> heartbeat(@PathVariable String pluginId, @RequestBody Heartbeat body) {
        registry.recordHeartbeat(pluginId, body == null ? null : body.getToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{pluginId}/deregister")
    public ResponseEntity<Void> deregister(@PathVariable String pluginId) {
        boolean removed = registry.deregister(pluginId);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // =============================================================== admin-facing

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PluginAdminDtos.PluginSummary> list() {
        List<PluginAdminDtos.PluginSummary> out = new ArrayList<>();
        for (RegisteredPlugin p : registry.all()) {
            out.add(PluginAdminMapper.toSummary(p, clock));
        }
        return out;
    }

    @GetMapping(value = "/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    public PluginAdminDtos.CapabilitySummary capabilities() {
        return PluginAdminMapper.toCapabilitySummary(registry.activeByCapability());
    }

    @GetMapping(value = "/{pluginId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PluginAdminDtos.PluginDetail> detail(@PathVariable String pluginId) {
        Optional<RegisteredPlugin> p = registry.findById(pluginId);
        if (p.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<PluginCallLog.Entry> recent = callLog.recentFor(pluginId, 50);
        return ResponseEntity.ok(PluginAdminMapper.toDetail(p.get(), recent, clock));
    }

    @GetMapping(value = "/{pluginId}/call-log", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PluginAdminDtos.CallLogEntry>> callLog(
            @PathVariable String pluginId,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        if (registry.findById(pluginId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<PluginCallLog.Entry> recent = callLog.recentFor(pluginId, limit);
        List<PluginAdminDtos.CallLogEntry> mapped = new ArrayList<>(recent.size());
        for (PluginCallLog.Entry e : recent) {
            mapped.add(new PluginAdminDtos.CallLogEntry(
                    e.capability, e.endpointKey, e.url, e.startedAt, e.latencyMillis,
                    e.succeeded, e.httpStatus, e.responseSummary));
        }
        return ResponseEntity.ok(mapped);
    }

    /**
     * Forwards the JSON config blob to the plugin's {@code /plugin/configure} endpoint.
     * Returns whatever the plugin returns, plus a stamped row in {@link PluginCallLog}. Plan
     * §8.8 requires schema validation against the plugin's {@code configSchema} before
     * forwarding — that wiring is deferred until the JSON-Schema validator lands in §9; for
     * Phase 4.7 the body is forwarded as-is and any validation failure surfaces as the
     * plugin's own 4xx response.
     */
    @PostMapping(value = "/{pluginId}/configure",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> configure(@PathVariable String pluginId,
                                       @RequestBody Map<String, Object> body) {
        Optional<RegisteredPlugin> opt = registry.findById(pluginId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(PluginErrorResponse.of("unknown_plugin", "no plugin registered with id: " + pluginId));
        }
        RegisteredPlugin plugin = opt.get();
        String url = plugin.getBaseUrl() + "/plugin/configure";
        Instant startedAt = clock.instant();
        long startNanos = System.nanoTime();
        boolean ok = false;
        Integer status = null;
        String summary = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Juke-Plugin-Id", plugin.getPluginId());
            ResponseEntity<String> resp = http.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            ok = true;
            status = resp.getStatusCode().value();
            summary = "OK " + status;
            return ResponseEntity.status(resp.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.getBody());
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            status = e.getStatusCode().value();
            summary = "HTTP " + status;
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            summary = "transport: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(PluginErrorResponse.of("plugin_unreachable",
                            "plugin " + pluginId + " unreachable: " + e.getMessage()));
        } finally {
            long elapsed = (System.nanoTime() - startNanos) / 1_000_000L;
            callLog.record(new PluginCallLog.Entry(
                    pluginId, "CONFIGURE", "configure", url,
                    startedAt, elapsed, ok, status, summary, Map.of()));
        }
    }

    // =============================================================== exception map

    @ExceptionHandler(PluginRegistry.UnknownPluginException.class)
    public ResponseEntity<PluginErrorResponse> handleUnknown(PluginRegistry.UnknownPluginException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(PluginErrorResponse.of("unknown_plugin", e.getMessage()));
    }

    @ExceptionHandler(PluginRegistry.InvalidTokenException.class)
    public ResponseEntity<PluginErrorResponse> handleInvalidToken(PluginRegistry.InvalidTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(PluginErrorResponse.of("stale_token", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PluginErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(PluginErrorResponse.of("invalid_registration", e.getMessage()));
    }
}
