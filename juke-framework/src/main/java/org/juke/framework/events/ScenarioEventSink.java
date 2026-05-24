package org.juke.framework.events;

/**
 * Framework-owned outbound channel for the four scenario events the Phase 3
 * validation layer emits. Mirrors the methods on
 * {@code org.juke.scenario.event.ScenarioEventPublisher} but lives in
 * {@code juke-framework} so the framework never compile-depends on the
 * scenario service.
 *
 * <p>The scenario service registers a bean implementing this interface via
 * {@link ScenarioEvents#setSink(ScenarioEventSink)} on application startup.
 * In tests / standalone replay (no Spring context) the sink remains the
 * {@link #NO_OP} default and event calls are silently dropped.
 */
public interface ScenarioEventSink {

    void publishInputValidation(String className, String methodName, int step,
                                String diffSummary, Long runId, Long useCaseId);

    void publishExceptionCaptured(String className, String methodName, int step,
                                  Throwable cause, Long runId, Long useCaseId,
                                  Long recordingId);

    void publishControllerMismatch(String className, String methodName, int step,
                                   String comparisonReport, Long runId, Long useCaseId);

    /** Default no-op sink used when no scenario service is wired in. */
    ScenarioEventSink NO_OP = new ScenarioEventSink() {
        @Override public void publishInputValidation(String c, String m, int s, String d, Long r, Long u) {}
        @Override public void publishExceptionCaptured(String c, String m, int s, Throwable t, Long r, Long u, Long rec) {}
        @Override public void publishControllerMismatch(String c, String m, int s, String d, Long r, Long u) {}
    };
}
