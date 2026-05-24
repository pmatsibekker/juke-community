package org.juke.remix.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.juke.framework.events.IgnoreRuleProvider;
import org.juke.framework.events.ReplayContext;
import org.juke.framework.events.ScenarioEvents;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.storage.JukeStorage;
import org.juke.framework.validation.FieldDiff;
import org.juke.framework.validation.FieldIgnoreRule;
import org.juke.framework.validation.InputDiffEngine;
import org.juke.framework.validation.InputValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Spring AOP advice that implements Phase 3 §5.4 — request/response capture
 * and comparison for any {@code @RestController} that also carries
 * {@code @JukeController}.
 *
 * <h3>Responsibilities by mode</h3>
 * <ul>
 *   <li><b>REPLAY</b> — capture the controller's actual response, look up the
 *       baseline {@code controller-capture/{Class}.{method}.{step}.json} sidecar
 *       in the active recording ZIP, run a leaf-by-leaf diff under the
 *       configured {@link FieldIgnoreRule}s, and publish a
 *       {@code ControllerMismatchEvent} on any mismatch (Plan §4.4 keeps the
 *       use case FAILED but doesn't abort siblings).</li>
 *   <li><b>RECORD</b> — write the live response to the same sidecar so the
 *       baseline is established for future replays.</li>
 *   <li><b>IGNORE / no mode</b> — proceed without instrumentation.</li>
 * </ul>
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

    /** Sidecar path inside the recording ZIP. Plan §5.4. */
    private static final String SIDECAR_DIR = "controller-capture/";

    /**
     * Match every method declared on a class annotated with
     * {@code @JukeController}. {@code within(@org.juke...JukeController *)}
     * picks up class-level type annotation; the response object becomes the
     * intercepted return value.
     */
    @Around("within(@org.juke.framework.annotation.JukeController *)")
    public Object aroundJukeController(ProceedingJoinPoint pjp) throws Throwable {
        Object response = pjp.proceed();

        String mode = currentMode();
        if (mode == null || mode.equalsIgnoreCase("IGNORE")) {
            return response;
        }

        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        String className = declaringClass.getName();
        String methodName = method.getName();

        ReplayContext.Scope scope = ReplayContext.current();
        int step = scope != null ? scope.step : 0;
        String sidecar = sidecarKey(declaringClass, methodName, step);

        JukeStorage storage = JukeRuntimeHolder.current().storage();

        if ("RECORD".equalsIgnoreCase(mode)) {
            recordBaseline(storage, sidecar, response);
            return response;
        }

        if ("REPLAY".equalsIgnoreCase(mode)) {
            compareAgainstBaseline(storage, sidecar, response,
                    declaringClass, methodName, step, scope);
        }

        return response;
    }

    // ------------------------------------------------------------------ record

    private void recordBaseline(JukeStorage storage, String sidecar, Object response) {
        if (storage == null) {
            LOG.debug("No active storage — skipping controller capture write for {}", sidecar);
            return;
        }
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response);
            storage.writeDirectEntry(sidecar, json);
            LOG.debug("Wrote controller capture sidecar {}", sidecar);
        } catch (Exception e) {
            // Capture must never break a real request.
            LOG.warn("Failed to write controller capture sidecar {}: {}", sidecar, e.toString());
        }
    }

    // ----------------------------------------------------------------- replay

    private void compareAgainstBaseline(JukeStorage storage, String sidecar, Object response,
                                        Class<?> declaringClass, String methodName, int step,
                                        ReplayContext.Scope scope) {
        if (storage == null) return;

        String baseline;
        try {
            baseline = storage.asString(sidecar);
        } catch (Exception e) {
            LOG.debug("No baseline sidecar {} (mode=REPLAY): {}", sidecar, e.toString());
            return;
        }
        if (baseline == null || baseline.isBlank()) return;

        try {
            JsonNode expected = MAPPER.readTree(baseline);
            JsonNode actual = MAPPER.valueToTree(response);

            IgnoreRuleProvider provider = ScenarioEvents.ignoreRuleProvider();
            List<FieldIgnoreRule> rules = provider != null
                    ? provider.findFor(declaringClass.getName(), methodName)
                    : List.of();

            InputValidationResult result = ENGINE.diff(
                    declaringClass.getName(), methodName, step, expected, actual, rules);

            if (result.hasDiffs()) {
                String summary = renderSummary(result.diffs());
                LOG.info("CONTROLLER_MISMATCH [{}.{}#{}]: {}",
                        declaringClass.getName(), methodName, step, summary);
                if (scope != null) {
                    ScenarioEvents.sink().publishControllerMismatch(
                            declaringClass.getName(), methodName, step, summary,
                            scope.runId, scope.useCaseId);
                }
            }
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse controller baseline {}: {}", sidecar, e.toString());
        }
    }

    // ---------------------------------------------------------------- helpers

    private String currentMode() {
        try {
            return JukeRuntimeHolder.current().mode().name();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String sidecarKey(Class<?> clazz, String method, int step) {
        return SIDECAR_DIR + clazz.getSimpleName() + "." + method + "." + step + ".json";
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
}
