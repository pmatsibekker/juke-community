package org.juke.framework.harness;

import java.util.Objects;
import java.util.UUID;

/**
 * Per-use-case execution context handed to a {@link UiHarness#executeUseCase} call.
 *
 * <p><strong>Phase 2 scope:</strong> minimal shape — just enough so the SPI compiles and the
 * {@link NoOpUiHarness} can be wired. Phase 4 ("Test Run Queue & Lifecycle") fleshes this out
 * with the full {@code RunPlan} fields described in §10.4 of the plan (browserMode, cookies,
 * uiArtefact bytes/handle, recordedSequence, etc.).
 *
 * <p>Marked non-final so the Phase 4 work can extend it without breaking source compatibility
 * for harness implementations written against this stub.
 */
public class UseCaseExecutionPlan {

    private final UUID useCaseExecutionId;
    private final UUID testRunId;
    private final String bundleName;
    private final int jukeRecordingVersion;
    private final Integer uiRecordingVersion; // nullable for Juke-only bundles

    public UseCaseExecutionPlan(UUID useCaseExecutionId,
                                UUID testRunId,
                                String bundleName,
                                int jukeRecordingVersion,
                                Integer uiRecordingVersion) {
        this.useCaseExecutionId = Objects.requireNonNull(useCaseExecutionId, "useCaseExecutionId");
        this.testRunId = Objects.requireNonNull(testRunId, "testRunId");
        this.bundleName = Objects.requireNonNull(bundleName, "bundleName");
        this.jukeRecordingVersion = jukeRecordingVersion;
        this.uiRecordingVersion = uiRecordingVersion;
    }

    public UUID useCaseExecutionId() { return useCaseExecutionId; }
    public UUID testRunId() { return testRunId; }
    public String bundleName() { return bundleName; }
    public int jukeRecordingVersion() { return jukeRecordingVersion; }

    /** {@code null} for Juke-only bundles (no UI side recorded). */
    public Integer uiRecordingVersion() { return uiRecordingVersion; }

    public boolean hasUiSide() { return uiRecordingVersion != null; }
}
