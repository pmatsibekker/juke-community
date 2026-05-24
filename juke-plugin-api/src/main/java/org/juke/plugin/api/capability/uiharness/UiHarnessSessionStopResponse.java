package org.juke.plugin.api.capability.uiharness;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Response body from {@code /plugin/v1/ui-harness/session-stop}. The plugin returns the captured
 * UI-side artefact bytes (e.g. a Playwright trace) base64-encoded so JSON transport works
 * cleanly. {@link #harnessId} echoes the harness identifier so remix can name the entry inside
 * the combined bundle ZIP correctly.
 *
 * <p>{@link #artefactPresent} is {@code false} for a session that the harness never observed
 * (e.g. the runner crashed before {@code session-start} reached the plugin, or the harness was
 * configured to skip capture for this session).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UiHarnessSessionStopResponse {

    private boolean artefactPresent;
    private String harnessId;
    private String artefactBase64;
    private String contentType;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public UiHarnessSessionStopResponse() {}

    public boolean isArtefactPresent() { return artefactPresent; }
    public void setArtefactPresent(boolean artefactPresent) { this.artefactPresent = artefactPresent; }
    public String getHarnessId() { return harnessId; }
    public void setHarnessId(String harnessId) { this.harnessId = harnessId; }
    public String getArtefactBase64() { return artefactBase64; }
    public void setArtefactBase64(String artefactBase64) { this.artefactBase64 = artefactBase64; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Map<String, Object> getMetadata() {
        return metadata == null ? Collections.emptyMap() : metadata;
    }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : metadata;
    }
}
