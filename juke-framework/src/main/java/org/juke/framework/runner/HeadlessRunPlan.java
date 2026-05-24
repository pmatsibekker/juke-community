package org.juke.framework.runner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Local mirror of the {@code RunPlan} contract documented in §10.4 of the Juke Scenario Testing
 * Plan and produced by {@code POST /service/runner/claim} (juke-remix-rest-service).
 *
 * <p>Mirrored here rather than imported from the REST service so {@code juke-framework} can
 * deserialise plans without depending on {@code juke-remix-rest-service} types — keeping the
 * dependency direction one-way (rest-service → framework, never the other way).
 *
 * <p>Only the fields the {@link HeadlessJukeRunner} actually reads are mirrored. Unknown
 * properties are ignored so adding fields to §10.4 in later phases doesn't break the runner.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeadlessRunPlan {

    public UUID runId;
    public String browserMode;
    public String uiHarness;
    public String suiteName;
    public String suiteDescription;
    public List<UseCaseEntry> useCases;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UseCaseEntry {
        public UUID useCaseExecutionId;
        public String useCaseName;
        public String useCaseDescription;
        public int ordinal;
        public FlowEntry flow;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FlowEntry {
        public UUID id;
        public String type;
        public String name;
        public String description;
        public String bundleName;
        public Integer jukeRecordingVersion;
        /** {@code null} for Juke-only flows. Presence flips this from headless to UI-driven. */
        public UiArtefactRef uiArtefact;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UiArtefactRef {
        public String harness;
        public Integer version;
        public String downloadUrl;
    }
}
