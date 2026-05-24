package org.juke.framework.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 4.5 — in-process headless runner. Polls {@code /service/runner/claim} with
 * {@code harnessFilter="none"}, walks the returned {@link HeadlessRunPlan}, and drives each use
 * case through the §8.5 lifecycle without a browser, Node, or Playwright.
 *
 * <p>Per use case the runner:
 * <ol>
 *   <li>Calls {@code GET /service/session/start?track=<bundleName>} to obtain a session id.</li>
 *   <li>Reports the session id via {@code POST /service/runner/runs/{id}/usecases/{uc}/start}.</li>
 *   <li>Replays recorded REST calls — Phase 7 controller-entry capture is not yet wired, so this
 *       step is currently a no-op (a {@code RUNTIME_FAILURE} finding is emitted only when the
 *       prior session creation failed; otherwise the use case passes silently).</li>
 *   <li>Calls {@code POST /service/runner/runs/{id}/usecases/{uc}/finish} with PASSED or FAILED.</li>
 * </ol>
 *
 * <p>After every use case has finished the runner posts a terminal
 * {@code POST /service/runner/runs/{id}/finish} with COMPLETED (all PASSED) or FAILED (any
 * failure). Heartbeats are emitted between use cases so the {@code RunReaper} does not reap a run
 * that is making forward progress.
 *
 * <p><strong>Activation:</strong> gated by {@code juke.headless-runner.enabled=true}. Off by
 * default so the bean does not spin up in environments that already host an external runner.
 * Disabled deployments continue to expose the runner endpoints; only the in-process polling
 * loop is suppressed.
 *
 * <p><strong>Threading:</strong> {@link #poll()} is invoked on Spring's scheduled-task thread
 * pool. {@link #tickOnce()} is the test-friendly entry point — it claims at most one run and
 * walks it to completion synchronously, returning the run id when work was done. Both methods
 * are idempotent w.r.t. an empty queue.
 */
@Component
@ConditionalOnProperty(name = "juke.headless-runner.enabled", havingValue = "true", matchIfMissing = false)
public class HeadlessJukeRunner {

    private static final Logger LOG = LoggerFactory.getLogger(HeadlessJukeRunner.class);

    /** Harness id this runner advertises on claim. Matches {@code NoOpUiHarness.ID}. */
    public static final String HARNESS_FILTER_NONE = "none";

    private final RestTemplate http;
    private volatile String baseUrl;
    private final String runnerId;

    public HeadlessJukeRunner(
            @Value("${juke.headless-runner.base-url:http://localhost:8080}") String baseUrl,
            @Value("${juke.headless-runner.runner-id:headless-juke-runner}") String runnerId) {
        this.http = new RestTemplate();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.runnerId = runnerId;
    }

    /**
     * Override the base URL after the bean has been constructed. Required for tests using
     * Spring Boot's {@code RANDOM_PORT} web environment, where {@code local.server.port} is
     * not bound to the {@link org.springframework.beans.factory.annotation.Value} placeholder
     * resolved at construction time.
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    // ============================================================== schedule entry

    /**
     * Scheduler-driven poll. Picks at most one run per tick so a single slow run cannot starve
     * the scheduler thread; the next tick re-enters and either picks up the same run's
     * continuation (it won't — the previous tick walks to terminal) or claims a fresh one.
     */
    @Scheduled(
            fixedDelayString = "${juke.headless-runner.poll-interval-ms:5000}",
            initialDelayString = "${juke.headless-runner.initial-delay-ms:2000}")
    public void poll() {
        try {
            tickOnce();
        } catch (Exception e) {
            // Never let a scheduled task die — log and let the next tick try again.
            LOG.error("HeadlessJukeRunner tick failed", e);
        }
    }

    // ============================================================== synchronous tick

    /**
     * Claim a single run (if any) and walk it to terminal status. Returns the run id when work
     * was done, {@code Optional.empty()} when the queue had nothing to claim. Exposed for the
     * Phase 4.5 acceptance test, which drives the runner deterministically rather than waiting
     * for the scheduler.
     */
    public Optional<UUID> tickOnce() {
        Optional<HeadlessRunPlan> claimed = claimNext();
        if (claimed.isEmpty()) return Optional.empty();
        HeadlessRunPlan plan = claimed.get();
        executeRun(plan);
        return Optional.of(plan.runId);
    }

    // =================================================================== claim

    private Optional<HeadlessRunPlan> claimNext() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runnerId", runnerId);
        body.put("harnessFilter", HARNESS_FILTER_NONE);

        ResponseEntity<HeadlessRunPlan> response;
        try {
            response = http.exchange(
                    baseUrl + "/service/runner/claim",
                    HttpMethod.POST,
                    jsonEntity(body),
                    HeadlessRunPlan.class);
        } catch (ResourceAccessException e) {
            LOG.warn("Cannot reach runner endpoint at {}: {}", baseUrl, e.getMessage());
            return Optional.empty();
        }
        if (response.getStatusCode().value() == 204) return Optional.empty();
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            LOG.warn("Unexpected /claim response status={} body={}",
                    response.getStatusCode(), response.getBody());
            return Optional.empty();
        }
        return Optional.of(response.getBody());
    }

    // ============================================================ execute one run

    private void executeRun(HeadlessRunPlan plan) {
        LOG.info("HeadlessJukeRunner claimed run={} suite='{}' useCases={}",
                plan.runId, plan.suiteName, plan.useCases == null ? 0 : plan.useCases.size());

        try {
            postNoBody("/service/runner/runs/" + plan.runId + "/start");
        } catch (Exception e) {
            LOG.error("Failed to start run {}: {}", plan.runId, e.getMessage());
            failRun(plan.runId, "Could not transition run to RUNNING: " + e.getMessage());
            return;
        }

        boolean anyFailed = false;
        if (plan.useCases != null) {
            for (HeadlessRunPlan.UseCaseEntry uc : plan.useCases) {
                try {
                    postNoBody("/service/runner/runs/" + plan.runId + "/heartbeat");
                } catch (Exception ignored) {
                    // Heartbeat best-effort — a single missed beat is benign within the grace window.
                }
                if (!executeUseCase(plan.runId, uc)) anyFailed = true;
            }
        }

        finishRun(plan.runId, anyFailed);
    }

    private boolean executeUseCase(UUID runId, HeadlessRunPlan.UseCaseEntry uc) {
        String bundleName = uc.flow == null ? null : uc.flow.bundleName;
        if (bundleName == null || bundleName.isBlank()) {
            LOG.warn("Run {} use case {} has no bundleName — skipping", runId, uc.useCaseExecutionId);
            postFinding(runId, uc.useCaseExecutionId,
                    "RUNTIME_FAILURE",
                    "HeadlessJukeRunner",
                    "Use case has no MAIN flow or its flow has no bundleName");
            finishUseCase(runId, uc.useCaseExecutionId, "FAILED");
            return false;
        }

        // 1) Open a session for this bundle.
        String sessionId;
        try {
            sessionId = startSession(bundleName);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            LOG.warn("session/start failed for bundle '{}' (run {}, use case {}): {}",
                    bundleName, runId, uc.useCaseExecutionId, e.getStatusCode());
            postFinding(runId, uc.useCaseExecutionId,
                    "RUNTIME_FAILURE",
                    e.getClass().getSimpleName(),
                    "session/start for track '" + bundleName + "' returned " + e.getStatusCode());
            finishUseCase(runId, uc.useCaseExecutionId, "FAILED");
            return false;
        } catch (Exception e) {
            LOG.error("Unexpected error opening session for bundle '{}' (run {}, use case {})",
                    bundleName, runId, uc.useCaseExecutionId, e);
            postFinding(runId, uc.useCaseExecutionId,
                    "RUNTIME_FAILURE",
                    e.getClass().getSimpleName(),
                    "session/start failed: " + e.getMessage());
            finishUseCase(runId, uc.useCaseExecutionId, "FAILED");
            return false;
        }

        // 2) Mark the use case RUNNING with the issued sessionId.
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sessionId", sessionId);
            postJson("/service/runner/runs/" + runId + "/usecases/" + uc.useCaseExecutionId + "/start", body);
        } catch (Exception e) {
            LOG.error("Failed to start use case {} on run {}: {}",
                    uc.useCaseExecutionId, runId, e.getMessage());
            finishUseCase(runId, uc.useCaseExecutionId, "FAILED");
            return false;
        }

        // 3) Replay recorded REST calls. Phase 7 will capture controller entries; until then this
        //    step is a no-op so the lifecycle remains exercisable end-to-end.
        //    Findings emitted by the replay step will land here in later phases.

        // 4) Finish use case PASSED.
        finishUseCase(runId, uc.useCaseExecutionId, "PASSED");
        return true;
    }

    private void finishRun(UUID runId, boolean anyFailed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", anyFailed ? "FAILED" : "COMPLETED");
        if (anyFailed) body.put("summary", "One or more use cases failed during headless replay");
        try {
            postJson("/service/runner/runs/" + runId + "/finish", body);
            LOG.info("HeadlessJukeRunner finished run {} status={}", runId,
                    anyFailed ? "FAILED" : "COMPLETED");
        } catch (Exception e) {
            LOG.error("Failed to finish run {}: {}", runId, e.getMessage());
        }
    }

    private void failRun(UUID runId, String summary) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "FAILED");
        body.put("summary", summary);
        try {
            postJson("/service/runner/runs/" + runId + "/finish", body);
        } catch (Exception ignored) {
            // Already logged by caller; nothing more we can do.
        }
    }

    // ================================================================ utility

    private String startSession(String trackName) {
        ResponseEntity<Map<String, Object>> response = http.exchange(
                baseUrl + "/service/session/start?track=" + trackName,
                HttpMethod.GET,
                null,
                mapType());
        Map<String, Object> body = response.getBody();
        if (body == null || body.get("sessionId") == null) {
            throw new IllegalStateException("session/start returned no sessionId");
        }
        return String.valueOf(body.get("sessionId"));
    }

    private void finishUseCase(UUID runId, UUID ucExecId, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        try {
            postJson("/service/runner/runs/" + runId + "/usecases/" + ucExecId + "/finish", body);
        } catch (Exception e) {
            LOG.error("Failed to finish use case {} (status={}) on run {}: {}",
                    ucExecId, status, runId, e.getMessage());
        }
    }

    private void postFinding(UUID runId, UUID ucExecId, String findingType,
                             String exceptionClass, String exceptionMessage) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("executionId", ucExecId);
        item.put("findingType", findingType);
        item.put("exceptionClass", exceptionClass);
        item.put("exceptionMessage", exceptionMessage);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("findings", List.of(item));
        try {
            postJson("/service/runner/runs/" + runId + "/findings", body);
        } catch (Exception e) {
            LOG.error("Failed to record finding on run {} use case {}: {}",
                    runId, ucExecId, e.getMessage());
        }
    }

    private void postNoBody(String path) {
        http.exchange(baseUrl + path, HttpMethod.POST, jsonEntity(null), Void.class);
    }

    private void postJson(String path, Map<String, Object> body) {
        http.exchange(baseUrl + path, HttpMethod.POST, jsonEntity(body), Void.class);
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static org.springframework.core.ParameterizedTypeReference<Map<String, Object>> mapType() {
        return (org.springframework.core.ParameterizedTypeReference) new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() { };
    }

    private static String stripTrailingSlash(String s) {
        return (s != null && s.endsWith("/")) ? s.substring(0, s.length() - 1) : s;
    }
}
