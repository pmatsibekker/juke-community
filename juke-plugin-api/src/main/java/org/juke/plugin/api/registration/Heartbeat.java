package org.juke.plugin.api.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.juke.plugin.api.PluginCapability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Heartbeat body POSTed by a plugin to {@code /service/plugins/{id}/heartbeat} on its expected
 * cadence (default 15s). The {@code token} must match the one returned by the most recent
 * registration; otherwise remix replies 401 and the plugin re-registers.
 *
 * <p>{@link #capabilityHealth} is a free-form per-capability health map — for example a
 * {@code UI_HARNESS} plugin might report {@code {"playwright": "OK", "queue-depth": 3}}. Remix
 * surfaces it as-is on the {@code GET /service/plugins/{id}} detail page; it does not affect
 * dispatch decisions.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Heartbeat {

    private String token;
    private String status;
    private Map<PluginCapability, Map<String, Object>> capabilityHealth = new LinkedHashMap<>();

    public Heartbeat() {}

    public Heartbeat(String token, String status) {
        this.token = token;
        this.status = status;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<PluginCapability, Map<String, Object>> getCapabilityHealth() {
        return capabilityHealth == null ? Collections.emptyMap() : capabilityHealth;
    }

    public void setCapabilityHealth(Map<PluginCapability, Map<String, Object>> capabilityHealth) {
        this.capabilityHealth = capabilityHealth == null ? new LinkedHashMap<>() : capabilityHealth;
    }
}
