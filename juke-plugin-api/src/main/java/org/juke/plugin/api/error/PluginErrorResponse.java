package org.juke.plugin.api.error;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error envelope used <em>both</em> directions on the plugin protocol:
 *
 * <ul>
 *   <li>Remix returns this from {@code /service/plugins/*} when a registration / heartbeat is
 *       rejected (e.g. unknown plugin id, stale token, capability list empty).</li>
 *   <li>Plugins return this from {@code /plugin/v1/...} capability endpoints when the plugin
 *       knew enough about the request to reject it cleanly (validation, transient unavailability,
 *       etc.). The {@code juke-plugin-sdk} default exception handler translates uncaught
 *       exceptions into this envelope so plugin authors don't have to write the boilerplate.</li>
 * </ul>
 *
 * <p>Conventionally the {@link #error} field uses the canonical strings from §8.8 — e.g.
 * {@code "unknown_plugin"}, {@code "stale_token"}, {@code "invalid_capability"} — so the admin
 * UI can render localised messages without having to parse the prose in {@link #message}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginErrorResponse {

    private String error;
    private String message;
    private Instant timestamp;
    private Map<String, Object> details = new LinkedHashMap<>();

    public PluginErrorResponse() {}

    public PluginErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public static PluginErrorResponse of(String error, String message) {
        return new PluginErrorResponse(error, message);
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getDetails() {
        return details == null ? Collections.emptyMap() : details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details == null ? new LinkedHashMap<>() : details;
    }
}
