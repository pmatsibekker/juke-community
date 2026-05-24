package org.juke.plugin.api.capability.transformer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request body for the two {@code RECORDING_TRANSFORMER} endpoints —
 * {@code /plugin/v1/transform/before-write} (called before remix persists a recording) and
 * {@code /plugin/v1/transform/after-read} (called after remix loads a recording for replay).
 *
 * <p>{@link #payloadBase64} is the raw entry contents; {@link #path} is the path within the
 * recording ZIP (e.g. {@code SomeService.$method.0.out.json}). The plugin returns a
 * {@link TransformResult} carrying possibly-mutated {@code payloadBase64} bytes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransformRequest {

    public enum Direction { BEFORE_WRITE, AFTER_READ }

    private Direction direction;
    private String path;
    private String payloadBase64;
    private String contentType;
    private Map<String, Object> recordingMetadata = new LinkedHashMap<>();
    private Map<String, Object> pluginConfig = new LinkedHashMap<>();

    public TransformRequest() {}

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getPayloadBase64() { return payloadBase64; }
    public void setPayloadBase64(String payloadBase64) { this.payloadBase64 = payloadBase64; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Map<String, Object> getRecordingMetadata() {
        return recordingMetadata == null ? Collections.emptyMap() : recordingMetadata;
    }
    public void setRecordingMetadata(Map<String, Object> recordingMetadata) {
        this.recordingMetadata = recordingMetadata == null ? new LinkedHashMap<>() : recordingMetadata;
    }
    public Map<String, Object> getPluginConfig() {
        return pluginConfig == null ? Collections.emptyMap() : pluginConfig;
    }
    public void setPluginConfig(Map<String, Object> pluginConfig) {
        this.pluginConfig = pluginConfig == null ? new LinkedHashMap<>() : pluginConfig;
    }
}
