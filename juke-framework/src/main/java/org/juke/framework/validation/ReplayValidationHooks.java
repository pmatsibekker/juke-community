package org.juke.framework.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.juke.framework.events.IgnoreRuleProvider;
import org.juke.framework.events.ReplayContext;
import org.juke.framework.events.ScenarioEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Glue between the proxy invoke loop ({@code ReplayHandler}) and the Phase 3
 * Phase 5 outbound event channel. Runs the {@link InputDiffEngine} only when:
 * <ul>
 *   <li>a {@link ReplayContext.Scope} is active on the current thread, and</li>
 *   <li>the {@link IgnoreRuleProvider} has not been left at the default
 *       {@link IgnoreRuleProvider#EMPTY} (so we don't allocate JsonNodes when
 *       no scenario service is wired in).</li>
 * </ul>
 * Otherwise the call is a cheap no-op so the framework remains usable
 * standalone.
 */
public final class ReplayValidationHooks {

    private static final Logger LOG = LoggerFactory.getLogger(ReplayValidationHooks.class);
    private static final InputDiffEngine ENGINE = new InputDiffEngine();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReplayValidationHooks() {}

    /**
     * Diff the recorded args against the live args, publishing an
     * {@code InputValidationEvent} when at least one mismatching leaf survives
     * the ignore rules.
     *
     * @param method      java.lang.reflect.Method being replayed
     * @param liveArgs    current invocation args (may be null)
     * @param recordedArgs recorded args from the {@code .args.json} sidecar
     *                     (may be null — caller passes whatever it parsed)
     */
    public static void runInputDiff(Class<?> targetClass, Method method,
                                    Object[] liveArgs, Object recordedArgs) {

        ReplayContext.Scope scope = ReplayContext.current();
        // No active run scope — no-op (e.g. dev-mode replay).
        if (scope == null) return;

        IgnoreRuleProvider provider = ScenarioEvents.ignoreRuleProvider();
        // Provider not yet registered — skip without serializing.
        if (provider == IgnoreRuleProvider.EMPTY) return;

        String className  = targetClass != null ? targetClass.getName() : "<unknown>";
        String methodName = method != null ? method.getName() : "<unknown>";
        int step = ReplayContext.incrementStep();

        try {
            JsonNode recordedNode = MAPPER.valueToTree(recordedArgs);
            JsonNode liveNode = MAPPER.valueToTree(liveArgs);
            List<FieldIgnoreRule> rules = provider.findFor(className, methodName);

            InputValidationResult result = ENGINE.diff(
                    className, methodName, step, recordedNode, liveNode, rules);

            if (result.hasDiffs()) {
                String summary = result.summary();
                LOG.info("INPUT_MISMATCH [{}.{}#{}]: {}", className, methodName, step, summary);
                ScenarioEvents.sink().publishInputValidation(
                        className, methodName, step, summary,
                        scope.runId, scope.useCaseId);
            }
        } catch (RuntimeException e) {
            // Diagnostics only — never fail the replay because the diff couldn't run.
            LOG.warn("Input-diff hook failed for {}.{}: {}", className, methodName, e.toString());
        }
    }

    /**
     * Publish an {@code ExceptionCapturedEvent} for an exception thrown by the
     * replayed-call site. Returns the original throwable unchanged so the
     * caller can {@code throw} it.
     */
    public static <T extends Throwable> T captureException(Class<?> targetClass, Method method,
                                                           T throwable) {
        ReplayContext.Scope scope = ReplayContext.current();
        if (scope == null) return throwable;

        String className  = targetClass != null ? targetClass.getName() : "<unknown>";
        String methodName = method != null ? method.getName() : "<unknown>";
        int step = scope.step; // current step, not incrementing

        try {
            ScenarioEvents.sink().publishExceptionCaptured(
                    className, methodName, step, throwable,
                    scope.runId, scope.useCaseId, scope.recordingId);
        } catch (RuntimeException e) {
            LOG.warn("Exception-capture hook failed for {}.{}: {}", className, methodName, e.toString());
        }
        return throwable;
    }
}
