package org.juke.plugin.api.capability.uiharness;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code POST /plugin/v1/ui-harness/session-stop} body — remix asks the harness for the captured
 * UI artefact for this session. The harness returns it in {@link UiHarnessSessionStopResponse}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UiHarnessSessionStopRequest {

    private String sessionId;

    public UiHarnessSessionStopRequest() {}

    public UiHarnessSessionStopRequest(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
