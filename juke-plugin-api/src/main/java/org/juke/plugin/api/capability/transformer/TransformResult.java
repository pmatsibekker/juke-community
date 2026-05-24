package org.juke.plugin.api.capability.transformer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response body from {@code /plugin/v1/transform/before-write} and
 * {@code /plugin/v1/transform/after-read}.
 *
 * <p>{@link #mutated} signals whether the plugin actually changed bytes — when {@code false}
 * remix can skip the rewrite pathway and reuse the original buffer. {@link #payloadBase64} is
 * the (possibly transformed) bytes; it is required when {@code mutated == true}, otherwise
 * remix uses the original input.
 *
 * <p>{@link #notes} is a free-form list of redactions / changes applied — surfaced verbatim in
 * the recording's audit trail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransformResult {

    private boolean mutated;
    private String payloadBase64;
    private List<String> notes = new ArrayList<>();

    public TransformResult() {}

    public boolean isMutated() { return mutated; }
    public void setMutated(boolean mutated) { this.mutated = mutated; }
    public String getPayloadBase64() { return payloadBase64; }
    public void setPayloadBase64(String payloadBase64) { this.payloadBase64 = payloadBase64; }
    public List<String> getNotes() {
        return notes == null ? Collections.emptyList() : notes;
    }
    public void setNotes(List<String> notes) { this.notes = notes; }
}
