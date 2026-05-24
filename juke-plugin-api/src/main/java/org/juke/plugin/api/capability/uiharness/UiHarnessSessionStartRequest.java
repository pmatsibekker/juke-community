package org.juke.plugin.api.capability.uiharness;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code POST /plugin/v1/ui-harness/session-start} body — remix tells a {@code UI_HARNESS}
 * plugin that a recording session has just begun, so the harness can spin up its capture
 * machinery (e.g. signal a Playwright runner to start trace capture).
 *
 * <p>{@link #sessionId} is the Juke session UUID; the plugin should key its own state on it so
 * the matching {@code session-stop} call can retrieve the right artefact.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UiHarnessSessionStartRequest {

    private String sessionId;
    private String trackName;
    private String harnessId;
    private String browserMode;
    private Map<String, Object> sessionMetadata = new LinkedHashMap<>();

    public UiHarnessSessionStartRequest() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTrackName() { return trackName; }
    public void setTrackName(String trackName) { this.trackName = trackName; }
    public String getHarnessId() { return harnessId; }
    public void setHarnessId(String harnessId) { this.harnessId = harnessId; }
    public String getBrowserMode() { return browserMode; }
    public void setBrowserMode(String browserMode) { this.browserMode = browserMode; }
    public Map<String, Object> getSessionMetadata() {
        return sessionMetadata == null ? Collections.emptyMap() : sessionMetadata;
    }
    public void setSessionMetadata(Map<String, Object> sessionMetadata) {
        this.sessionMetadata = sessionMetadata == null ? new LinkedHashMap<>() : sessionMetadata;
    }
}
