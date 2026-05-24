package org.juke.remix.plugin;

import org.juke.plugin.api.PluginCapability;
import org.juke.plugin.api.error.PluginErrorResponse;
import org.juke.plugin.api.paths.PluginPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Routes capability calls from remix into a registered plugin's {@code baseUrl + path}. Wraps
 * the bookkeeping the plan calls out in §5.5 / §4.7 bullet 3:
 *
 * <ul>
 *   <li>Resolve the target via {@link CapabilityResolver}.</li>
 *   <li>Sign the body with the {@code sharedSecret} from registration ({@code X-Juke-Plugin-Signature}).</li>
 *   <li>Time the call and stamp a row in {@link PluginCallLog}.</li>
 *   <li>Trip a per-plugin circuit breaker after 3 consecutive failures — subsequent calls
 *       fail fast for 30 seconds without hitting the network.</li>
 * </ul>
 *
 * <p>Failures map to typed exceptions:
 * <ul>
 *   <li>{@link PluginUnavailableException} — no active plugin / circuit open / network unreachable.</li>
 *   <li>{@link PluginRejectedException} — plugin returned 4xx/5xx with a {@link PluginErrorResponse}.</li>
 * </ul>
 *
 * <p>This class is the {@code PluginDispatcher} described in §5.5; "Dispatcher" is reserved by
 * an existing class in remix's runner package, so the implementation lives here as
 * {@code PluginInvoker}.
 */
@Component
public class PluginInvoker {

    private static final Logger LOG = LoggerFactory.getLogger(PluginInvoker.class);
    private static final int CIRCUIT_TRIP_FAILURES = 3;
    private static final Duration CIRCUIT_OPEN_FOR = Duration.ofSeconds(30);

    private final CapabilityResolver resolver;
    private final PluginCallLog callLog;
    private final RestTemplate http;
    private final Clock clock;

    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Instant>> circuitOpenUntil = new ConcurrentHashMap<>();

    @Autowired
    public PluginInvoker(CapabilityResolver resolver, PluginCallLog callLog) {
        this(resolver, callLog, defaultRestTemplate(), Clock.systemUTC());
    }

    /** Visible for tests. */
    public PluginInvoker(CapabilityResolver resolver, PluginCallLog callLog,
                         RestTemplate http, Clock clock) {
        this.resolver = resolver;
        this.callLog = callLog;
        this.http = http;
        this.clock = clock;
    }

    private static RestTemplate defaultRestTemplate() {
        return new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(60))
                .build();
    }

    public <REQ, RES> RES invoke(PluginCapability capability,
                                 String pluginIdOrNull,
                                 String endpointKey,
                                 String path,
                                 REQ request,
                                 Class<RES> responseType) {
        Optional<RegisteredPlugin> target = resolver.resolve(capability, pluginIdOrNull);
        if (target.isEmpty()) {
            throw new PluginUnavailableException(
                    "no active plugin for capability " + capability
                            + (pluginIdOrNull == null ? "" : " (requested id=" + pluginIdOrNull + ")"));
        }
        RegisteredPlugin plugin = target.get();

        if (circuitIsOpen(plugin.getPluginId())) {
            throw new PluginUnavailableException(
                    "circuit breaker open for plugin " + plugin.getPluginId());
        }

        String url = plugin.getBaseUrl() + path;
        Instant startedAt = clock.instant();
        long startMillis = System.nanoTime();
        boolean ok = false;
        Integer status = null;
        String summary = null;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Juke-Plugin-Id", plugin.getPluginId());
            headers.set("X-Juke-Plugin-Endpoint", endpointKey);
            String signature = signature(plugin.getPluginSharedSecret(),
                    plugin.getPluginId() + ":" + endpointKey + ":" + url);
            if (signature != null) {
                headers.set("X-Juke-Plugin-Signature", signature);
            }
            ResponseEntity<RES> resp = http.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);
            ok = true;
            status = resp.getStatusCode().value();
            summary = "OK " + status;
            consecutiveFailures.computeIfAbsent(plugin.getPluginId(), k -> new AtomicInteger()).set(0);
            return resp.getBody();
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            status = e.getStatusCode().value();
            summary = "HTTP " + status + ": " + truncate(e.getResponseBodyAsString(), 200);
            recordFailure(plugin.getPluginId());
            throw new PluginRejectedException(
                    "plugin " + plugin.getPluginId() + " rejected " + endpointKey + ": " + summary, e);
        } catch (ResourceAccessException e) {
            summary = "transport: " + e.getMessage();
            recordFailure(plugin.getPluginId());
            throw new PluginUnavailableException(
                    "plugin " + plugin.getPluginId() + " unreachable: " + e.getMessage(), e);
        } finally {
            long elapsedMillis = (System.nanoTime() - startMillis) / 1_000_000L;
            Map<String, Object> tags = new LinkedHashMap<>();
            tags.put("baseUrl", plugin.getBaseUrl());
            callLog.record(new PluginCallLog.Entry(
                    plugin.getPluginId(),
                    capability.name(),
                    endpointKey,
                    url,
                    startedAt,
                    elapsedMillis,
                    ok,
                    status,
                    summary,
                    tags));
        }
    }

    public boolean isCircuitOpen(String pluginId) {
        return circuitIsOpen(pluginId);
    }

    /** Visible for tests — let acceptance tests reset breaker state between scenarios. */
    public void resetCircuit(String pluginId) {
        consecutiveFailures.remove(pluginId);
        circuitOpenUntil.remove(pluginId);
    }

    /**
     * Convenience overload — pulls the canonical path for capabilities that have exactly one
     * endpoint (USE_CASE_SUGGESTION, SCAFFOLD_GENERATION). For UI_HARNESS / RECORDING_TRANSFORMER
     * callers must supply the path explicitly.
     */
    public <REQ, RES> RES invoke(PluginCapability capability,
                                 String pluginIdOrNull,
                                 REQ request,
                                 Class<RES> responseType) {
        String path = PluginPaths.singleEndpointFor(capability);
        String key = endpointKeyFromPath(path);
        return invoke(capability, pluginIdOrNull, key, path, request, responseType);
    }

    private static String endpointKeyFromPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private boolean circuitIsOpen(String pluginId) {
        AtomicReference<Instant> opened = circuitOpenUntil.get(pluginId);
        if (opened == null) return false;
        Instant until = opened.get();
        if (until == null) return false;
        if (clock.instant().isAfter(until)) {
            circuitOpenUntil.remove(pluginId);
            consecutiveFailures.computeIfPresent(pluginId, (k, v) -> { v.set(0); return v; });
            return false;
        }
        return true;
    }

    private void recordFailure(String pluginId) {
        int n = consecutiveFailures
                .computeIfAbsent(pluginId, k -> new AtomicInteger())
                .incrementAndGet();
        if (n >= CIRCUIT_TRIP_FAILURES) {
            Instant openUntil = clock.instant().plus(CIRCUIT_OPEN_FOR);
            circuitOpenUntil
                    .computeIfAbsent(pluginId, k -> new AtomicReference<>())
                    .set(openUntil);
            LOG.warn("plugin {} circuit breaker tripped after {} consecutive failures (open until {})",
                    pluginId, n, openUntil);
        }
    }

    private static String signature(String secret, String message) {
        if (secret == null || secret.isEmpty()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (Exception e) {
            LOG.debug("HMAC signing failed — proceeding without signature", e);
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    public static class PluginUnavailableException extends RuntimeException {
        public PluginUnavailableException(String msg) { super(msg); }
        public PluginUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    public static class PluginRejectedException extends RuntimeException {
        public PluginRejectedException(String msg, Throwable cause) { super(msg, cause); }
    }
}
