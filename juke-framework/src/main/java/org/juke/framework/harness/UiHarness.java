package org.juke.framework.harness;

import org.juke.framework.session.JukeSessionContext;

import java.util.Optional;

/**
 * Service Provider Interface every UI testing harness implements to integrate with Juke.
 *
 * <p>The {@code juke-framework} module never imports a concrete UI tool (Playwright, Cypress,
 * Selenium, ...). Instead, {@code juke-remix-rest-service} discovers all {@code UiHarness} beans
 * on the classpath at startup and elects one as {@code @Primary} based on the
 * {@code juke.ui-harness} property. See {@link NoOpUiHarness} for the default implementation
 * used when {@code juke.ui-harness=none} (or unset).
 *
 * <p>Per Phase 2 / D15 of the Scenario Testing Plan: this SPI is born in Phase 2; only the
 * {@link NoOpUiHarness} default ships with the framework. Real harnesses (e.g.
 * {@code juke-plugin-playwright-harness}) arrive in Phase 6 once the plugin platform exists.
 */
public interface UiHarness {

    /**
     * Stable identifier for this harness — used as the directory key inside combined bundle ZIPs
     * (e.g. {@code playwright.zip}) and as the {@code harness} column on {@code ui_recordings}.
     * Must be lowercase, alphanumeric, no spaces. Reserved value: {@code "none"} for the no-op.
     */
    String id();

    /**
     * Describes the artefact this harness produces per recording session — e.g. for Playwright,
     * a trace ZIP. Used by the admin UI to render harness-aware download labels and by the
     * bundle packager to validate manifest contents.
     */
    UiArtefactDescriptor describeArtefact();

    /**
     * Invoked at the moment {@code /service/juke/start} fires. Implementations may begin their own
     * capture (e.g. open a Playwright trace). The {@link JukeSessionContext} carries the
     * {@code JUKE_SESSION_ID}, {@code JUKE_TRACK}, and {@code JUKE_LOG_TRACE_ID} cookies set by
     * the cookie-proposal session layer.
     *
     * <p>Implementations must not block; expensive setup should happen asynchronously.
     */
    void onSessionStart(JukeSessionContext ctx);

    /**
     * Invoked at the moment {@code /service/juke/stop} fires. Implementations finalise their
     * capture and return the resulting ZIP bytes, or {@link Optional#empty()} if the harness
     * was inactive for this session (e.g. Playwright runner never connected). Returning empty
     * causes {@code /service/juke/stop} to respond with a plain Juke ZIP rather than a
     * combined bundle.
     */
    Optional<byte[]> onSessionStop(JukeSessionContext ctx);

    /**
     * @return {@code true} if this harness can drive the UI during replay (claim a run, run a
     *         spec, post findings). Capture-only harnesses return {@code false}; the
     *         {@link NoOpUiHarness} also returns {@code false} since headless replay is owned
     *         by the in-process {@code HeadlessJukeRunner} (Phase 4.5).
     */
    boolean canExecuteRecording();

    /**
     * Hand off a single use-case execution to the harness's external runner. For
     * {@link NoOpUiHarness} this is a no-op; for the Playwright harness this enqueues a
     * run-plan entry that the {@code juke-playwright-runner} polls for.
     *
     * <p>Returns immediately. The harness reports completion via the runner REST API
     * ({@code /service/runner/runs/{id}/finish}, etc.) — Phase 4.
     *
     * <p>The full shape of {@link UseCaseExecutionPlan} is defined in Phase 4 alongside the
     * run dispatcher; the Phase 2 placeholder carries enough context for the SPI to compile.
     */
    void executeUseCase(UseCaseExecutionPlan plan);
}
