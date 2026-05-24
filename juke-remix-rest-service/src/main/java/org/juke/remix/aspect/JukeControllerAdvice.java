package org.juke.remix.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.juke.framework.events.IgnoreRuleProvider;
import org.juke.framework.events.ReplayContext;
import org.juke.framework.events.ScenarioEvents;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.juke.framework.storage.JukeStorage;
import org.juke.framework.validation.FieldDiff;
import org.juke.framework.validation.FieldIgnoreRule;
import org.juke.framework.validation.InputDiffEngine;
import org.juke.framework.validation.InputValidationResult;
import org.juke.framework.validation.JukeIgnorableScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring AOP advice that implements request/response capture and comparison
 * for any {@code @RestController} that also carries {@code @JukeController}.
 *
 * <h3>Two sidecars per controller call</h3>
 * <ul>
 *   <li>{@code controller-capture/{Class}.{method}.{step}.request.json} — the
 *       inbound HTTP request: method, URI, query parameters, and the
 *       {@code @RequestBody} payload (if any). Lets a HAR file or a captured
 *       cURL invocation be diffed against the recording on the input half.</li>
 *   <li>{@code controller-capture/{Class}.{method}.{step}.json} — the
 *       controller's response object (status + body), as before.</li>
 * </ul>
 *
 * <h3>Responsibilities by mode</h3>
 * <ul>
 *   <li><b>RECORD</b> — write both sidecars; the baseline is established for
 *       future replays.</li>
 *   <li><b>REPLAY</b> — read both baselines, diff each one against the live
 *       inputs/outputs through {@link InputDiffEngine}, and publish a
 *       {@code ControllerMismatchEvent} per side that differs. Summaries are
 *       prefixed {@code REQ[...]} or {@code RESP[...]} so the side is
 *       attributable. Missing request-side baselines (older recordings) are
 *       silently skipped — backward-compatible.</li>
 *   <li><b>IGNORE / no mode</b> — proceed without instrumentation.</li>
 * </ul>
 *
 * <h3>{@code @JukeIgnorable} on controller DTOs</h3>
 * <p>{@link JukeIgnorableScanner#scan} reflects on the request body's runtime
 * type and the response's runtime type for {@code @JukeIgnorable} fields and
 * produces {@link FieldIgnoreRule}s anchored at {@code $.body.…} (request) and
 * {@code $.…} (response). These rules are merged with whatever the optional
 * {@link IgnoreRuleProvider} sink supplies, so the feature works standalone
 * without scenario-service installed.
 *
 * <p>Whatever the verdict, the advice always returns the controller's actual
 * response so application traffic continues uninterrupted.
 */
@Aspect
@Component
public class JukeControllerAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(JukeControllerAdvice.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final InputDiffEngine ENGINE = new InputDiffEngine();

    /** Sidecar directory inside the recording ZIP. */
    private static final String SIDECAR_DIR = "controller-capture/";

    /**
     * Per-(scope, class, method) call counter, 1-based to match the seam-side
     * sequence convention used by {@code DataProgramSchedule}
     * ({@code IFoo.bar.1.json}, {@code .2.json}, …). The scope prefix is the
     * cookie session id (so two concurrent replays don't collide) or the empty
     * string for the global record/replay flow. Reset by
     * {@link #resetCounters()} when a new global recording/replay begins.
     */
    private static final ConcurrentHashMap<String, AtomicInteger> STEP_COUNTERS = new ConcurrentHashMap<>();

    /**
     * Optional injection of the request-scoped {@link JukeSessionContext}
     * (Spring auto-wires a scoped proxy). Present whenever the framework is
     * enabled ({@code juke.enabled=true}); absent only in test contexts that
     * construct the advice directly without a Spring context.
     */
    @Autowired(required = false)
    private JukeSessionContext sessionContext;

    @Autowired(required = false)
    private SessionRegistry sessionRegistry;

    @Around("within(@org.juke.framework.annotation.JukeController *)")
    public Object aroundJukeController(ProceedingJoinPoint pjp) throws Throwable {
        SessionScope session = resolveSessionScope();
        String effectiveMode = session != null ? "REPLAY" : currentMode();
        boolean modeActive = effectiveMode != null && !effectiveMode.equalsIgnoreCase("IGNORE");

        // Snapshot the request before proceed(). Pure read-only work; never
        // allowed to break the request path.
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        RequestSnapshot req = null;
        Class<?> bodyType = null;
        if (modeActive) {
            try {
                req = RequestSnapshot.capture(method, pjp.getArgs());
                bodyType = findBodyType(method, pjp.getArgs());
            } catch (Throwable t) {
                LOG.debug("Failed to snapshot inbound request: {}", t.toString());
            }
        }

        Object response = pjp.proceed();

        if (!modeActive) return response;

        Class<?> declaringClass = method.getDeclaringClass();
        String methodName = method.getName();
        ReplayContext.Scope scope = ReplayContext.current();
        String stepScopeId = session != null ? session.sessionId : "";
        int step = nextStep(stepScopeId, declaringClass, methodName);

        JukeStorage storage = session != null ? session.dao : JukeRuntimeHolder.current().storage();

        if ("RECORD".equalsIgnoreCase(effectiveMode)) {
            writeSidecar(storage, requestSidecarKey(declaringClass, methodName, step),
                    serializeRequest(req));
            writeSidecar(storage, responseSidecarKey(declaringClass, methodName, step),
                    serializeResponse(response));
            return response;
        }

        if ("REPLAY".equalsIgnoreCase(effectiveMode)) {
            compareRequest(storage, declaringClass, methodName, step, req, bodyType, scope);
            compareResponse(storage, declaringClass, methodName, step, response, scope);
        }

        return response;
    }

    /**
     * Returns the active cookie-session scope (DAO + sessionId) if the request
     * carried a valid {@code JUKE_SESSION_ID} cookie, else {@code null}. The
     * session-aware seam-level replay never flips
     * {@link JukeRuntimeHolder#current()}'s mode to REPLAY, so the advice has
     * to check the request-scoped session context independently to detect
     * cookie-driven replay.
     */
    private SessionScope resolveSessionScope() {
        if (sessionContext == null || sessionRegistry == null) return null;
        try {
            if (!sessionContext.isPlaybackActive()) return null;
            String sessionId = sessionContext.getSessionId();
            if (sessionId == null) return null;
            return sessionRegistry.get(sessionId)
                    .map(e -> new SessionScope(sessionId, e))
                    .orElse(null);
        } catch (Throwable t) {
            // Outside a request scope (e.g. background thread) — silently fall
            // back to the global runtime.
            return null;
        }
    }

    private record SessionScope(String sessionId, JukeStorage dao) {
        SessionScope(String sessionId, JukeSessionEntry entry) {
            this(sessionId, entry.getDao());
        }
    }

    // ============================================================== RECORD

    private void writeSidecar(JukeStorage storage, String sidecar, String json) {
        if (storage == null || json == null) {
            LOG.debug("Skipping write to {} (storage={}, json={})", sidecar, storage, json != null);
            return;
        }
        try {
            storage.writeDirectEntry(sidecar, json);
            LOG.debug("Wrote controller capture sidecar {}", sidecar);
        } catch (Exception e) {
            // Capture must never break a real request.
            LOG.warn("Failed to write controller capture sidecar {}: {}", sidecar, e.toString());
        }
    }

    private static String serializeRequest(RequestSnapshot req) {
        if (req == null) return null;
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(req.toJson(MAPPER));
        } catch (Exception e) {
            LOG.warn("Failed to serialize request snapshot: {}", e.toString());
            return null;
        }
    }

    private static String serializeResponse(Object response) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (Exception e) {
            LOG.warn("Failed to serialize response: {}", e.toString());
            return null;
        }
    }

    // ============================================================== REPLAY

    private void compareRequest(JukeStorage storage,
                                Class<?> declaringClass, String methodName, int step,
                                RequestSnapshot req, Class<?> bodyType,
                                ReplayContext.Scope scope) {
        if (storage == null || req == null) return;
        String sidecar = requestSidecarKey(declaringClass, methodName, step);

        String baseline = readBaseline(storage, sidecar);
        // Missing request baselines are normal for older recordings — silent skip.
        if (baseline == null) return;

        try {
            JsonNode expected = MAPPER.readTree(baseline);
            JsonNode actual = req.toJson(MAPPER);

            List<FieldIgnoreRule> rules = mergedRules(declaringClass, methodName, bodyType, "$.body");
            InputValidationResult result = ENGINE.diff(
                    declaringClass.getName(), methodName, step, expected, actual, rules);

            if (result.hasDiffs()) {
                String summary = "REQ[" + renderSummary(result.diffs()) + "]";
                LOG.info("CONTROLLER_MISMATCH [{}.{}#{}]: {}",
                        declaringClass.getName(), methodName, step, summary);
                if (scope != null) {
                    ScenarioEvents.sink().publishControllerMismatch(
                            declaringClass.getName(), methodName, step, summary,
                            scope.runId, scope.useCaseId);
                }
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse request baseline {}: {}", sidecar, e.toString());
        }
    }

    private void compareResponse(JukeStorage storage,
                                 Class<?> declaringClass, String methodName, int step,
                                 Object response, ReplayContext.Scope scope) {
        if (storage == null) return;
        String sidecar = responseSidecarKey(declaringClass, methodName, step);

        String baseline = readBaseline(storage, sidecar);
        if (baseline == null) return;

        try {
            JsonNode expected = MAPPER.readTree(baseline);
            JsonNode actual = MAPPER.valueToTree(response);

            Class<?> responseType = response == null ? null : response.getClass();
            List<FieldIgnoreRule> rules = mergedRules(declaringClass, methodName, responseType, "$");
            InputValidationResult result = ENGINE.diff(
                    declaringClass.getName(), methodName, step, expected, actual, rules);

            if (result.hasDiffs()) {
                String summary = "RESP[" + renderSummary(result.diffs()) + "]";
                LOG.info("CONTROLLER_MISMATCH [{}.{}#{}]: {}",
                        declaringClass.getName(), methodName, step, summary);
                if (scope != null) {
                    ScenarioEvents.sink().publishControllerMismatch(
                            declaringClass.getName(), methodName, step, summary,
                            scope.runId, scope.useCaseId);
                }
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse response baseline {}: {}", sidecar, e.toString());
        }
    }

    private static String readBaseline(JukeStorage storage, String sidecar) {
        // JukeStorage.asString() auto-appends ".json"; writeDirectEntry() takes
        // the full key. The sidecar paths used by this aspect already include
        // the .json suffix (so writeDirectEntry receives a valid filename), so
        // we strip it before calling asString — otherwise the lookup goes
        // looking for "...request.json.json" which never exists, and every
        // replay silently no-ops instead of diffing.
        String identifier = sidecar.endsWith(".json")
                ? sidecar.substring(0, sidecar.length() - ".json".length())
                : sidecar;
        try {
            String s = storage.asString(identifier);
            return (s == null || s.isBlank()) ? null : s;
        } catch (Exception e) {
            LOG.debug("No baseline sidecar {} : {}", sidecar, e.toString());
            return null;
        }
    }

    /**
     * Merge ignore rules from two sources: the (optional) scenario-service
     * provider and the reflective {@link IgnorableScanner}. Both sets feed the
     * same engine; jsonPath-keyed last-write-wins ordering inside
     * {@link InputDiffEngine#diff} resolves overlaps.
     */
    private static List<FieldIgnoreRule> mergedRules(Class<?> declaringClass, String methodName,
                                                     Class<?> rootType, String rootPath) {
        IgnoreRuleProvider provider = ScenarioEvents.ignoreRuleProvider();
        List<FieldIgnoreRule> providerRules = provider != null
                ? provider.findFor(declaringClass.getName(), methodName)
                : List.of();
        List<FieldIgnoreRule> reflectedRules = JukeIgnorableScanner.scan(rootType, rootPath);
        if (providerRules.isEmpty()) return reflectedRules;
        if (reflectedRules.isEmpty()) return providerRules;
        List<FieldIgnoreRule> out = new ArrayList<>(providerRules.size() + reflectedRules.size());
        out.addAll(reflectedRules);
        out.addAll(providerRules);
        return out;
    }

    // ============================================================== helpers

    private String currentMode() {
        try {
            return JukeRuntimeHolder.current().mode().name();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String responseSidecarKey(Class<?> clazz, String method, int step) {
        return SIDECAR_DIR + clazz.getSimpleName() + "." + method + "." + step + ".json";
    }

    private static String requestSidecarKey(Class<?> clazz, String method, int step) {
        return SIDECAR_DIR + clazz.getSimpleName() + "." + method + "." + step + ".request.json";
    }

    private static int nextStep(String scopeId, Class<?> clazz, String method) {
        String key = (scopeId == null ? "" : scopeId) + "|" + clazz.getName() + "#" + method;
        return STEP_COUNTERS.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Reset the per-(class, method) controller-call counters. Called by
     * {@code RecordingServiceImpl.start()} and {@code ReplayServiceImpl.start()}
     * so each new recording or replay numbers its sidecars from {@code .1}.
     */
    public static void resetCounters() {
        STEP_COUNTERS.clear();
    }

    private static String renderSummary(List<FieldDiff> diffs) {
        if (diffs == null || diffs.isEmpty()) return "no diffs";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diffs.size(); i++) {
            if (i > 0) sb.append("; ");
            FieldDiff d = diffs.get(i);
            sb.append(d.jsonPath())
              .append(" expected=").append(d.expected())
              .append(" actual=").append(d.actual());
        }
        return sb.toString();
    }

    /** Returns the runtime class of the {@code @RequestBody} argument, or {@code null}. */
    private static Class<?> findBodyType(Method method, Object[] args) {
        Parameter[] params = method.getParameters();
        for (int i = 0; i < params.length && i < args.length; i++) {
            if (params[i].isAnnotationPresent(RequestBody.class)) {
                return args[i] != null ? args[i].getClass() : params[i].getType();
            }
        }
        return null;
    }

    // ============================================================== snapshot

    /**
     * Frozen view of an inbound HTTP request, captured by the advice before
     * the controller method runs. Serializes as
     * {@code {"method":..,"uri":..,"query":{..},"body":..}}.
     *
     * <p>Headers are intentionally omitted from the snapshot: bearer tokens,
     * cookies, and {@code X-Juke-*} infrastructure headers vary per session
     * and would defeat diffing.
     */
    static final class RequestSnapshot {
        final String httpMethod;
        final String uri;
        final Map<String, String[]> query;
        final JsonNode body;

        private RequestSnapshot(String httpMethod, String uri,
                                Map<String, String[]> query, JsonNode body) {
            this.httpMethod = httpMethod;
            this.uri = uri;
            this.query = query;
            this.body = body;
        }

        static RequestSnapshot capture(Method method, Object[] args) {
            HttpServletRequest http = currentHttpRequest();
            String httpMethod = http != null ? http.getMethod() : null;
            String uri = http != null ? http.getRequestURI() : null;
            Map<String, String[]> query = http != null ? http.getParameterMap() : null;
            JsonNode body = extractBody(method, args);
            return new RequestSnapshot(httpMethod, uri, query, body);
        }

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode root = mapper.createObjectNode();
            if (httpMethod != null) root.put("method", httpMethod);
            if (uri != null) root.put("uri", uri);
            if (query != null && !query.isEmpty()) {
                ObjectNode q = root.putObject("query");
                for (Map.Entry<String, String[]> e : query.entrySet()) {
                    String[] v = e.getValue();
                    if (v == null || v.length == 0) {
                        q.putNull(e.getKey());
                    } else if (v.length == 1) {
                        q.put(e.getKey(), v[0]);
                    } else {
                        com.fasterxml.jackson.databind.node.ArrayNode arr = q.putArray(e.getKey());
                        for (String s : v) arr.add(s);
                    }
                }
            }
            if (body != null) root.set("body", body);
            return root;
        }

        private static HttpServletRequest currentHttpRequest() {
            try {
                ServletRequestAttributes attrs =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                return attrs != null ? attrs.getRequest() : null;
            } catch (Throwable t) {
                return null;
            }
        }

        private static JsonNode extractBody(Method method, Object[] args) {
            Parameter[] params = method.getParameters();
            for (int i = 0; i < params.length && i < args.length; i++) {
                if (params[i].isAnnotationPresent(RequestBody.class)) {
                    return args[i] == null ? null : MAPPER.valueToTree(args[i]);
                }
            }
            return null;
        }
    }
}
