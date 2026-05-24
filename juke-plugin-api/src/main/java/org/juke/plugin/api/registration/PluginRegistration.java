package org.juke.plugin.api.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code POST /service/plugins/register} request body. Plugins POST this on their own
 * startup; remix replies with a {@link PluginRegistrationResponse} carrying a freshly minted
 * {@code pluginToken} that the plugin must include on every subsequent heartbeat.
 *
 * <p>See §5.3 ("Registration Handshake") and §8.8 of the Juke Scenario Testing Plan.
 *
 * <p>Field-by-field, the canonical body looks like:
 * <pre>{@code
 *   {
 *     "pluginId": "juke-plugin-agent-anthropic",
 *     "displayName": "Anthropic AI Agent",
 *     "version": "1.0.0",
 *     "baseUrl": "http://agent-anthropic:8090",
 *     "capabilities": [
 *       { "capability": "USE_CASE_SUGGESTION", "displayMetadata": {...}, "configSchema": {...} },
 *       { "capability": "SCAFFOLD_GENERATION", ... }
 *     ],
 *     "uiHints": { "primaryActionLabel": "Generate Suggestions", "iconName": "robot" },
 *     "healthCheckPath": "/actuator/health",
 *     "expectedHeartbeatIntervalSeconds": 15,
 *     "sharedSecret": "<HMAC key for outbound calls from remix to plugin>"
 *   }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginRegistration {

    private String pluginId;
    private String displayName;
    private String version;
    /** Externally-reachable base URL, e.g. {@code http://agent-anthropic:8090}. */
    private String baseUrl;

    private List<CapabilityDescriptor> capabilities = new ArrayList<>();

    /** Free-form admin-ui hints (icon name, primary action label, etc.). Optional. */
    private Map<String, Object> uiHints = new LinkedHashMap<>();

    /** Plugin's health endpoint path. Optional; remix uses it for richer "Plugin detail" pages. */
    private String healthCheckPath;

    /**
     * Plugin's own heartbeat cadence in seconds. Lets remix tune the staleness threshold per
     * plugin (still capped at the global default). {@code null} means "use the global default
     * (60s)".
     */
    private Integer expectedHeartbeatIntervalSeconds;

    /**
     * Plugin-issued HMAC secret. Remix signs outbound capability calls with this. Stored only
     * in the in-memory registry — never persisted, never logged.
     */
    private String sharedSecret;

    public PluginRegistration() {}

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<CapabilityDescriptor> getCapabilities() {
        return capabilities == null ? Collections.emptyList() : capabilities;
    }

    public void setCapabilities(List<CapabilityDescriptor> capabilities) {
        this.capabilities = capabilities == null ? new ArrayList<>() : capabilities;
    }

    public Map<String, Object> getUiHints() {
        return uiHints == null ? Collections.emptyMap() : uiHints;
    }

    public void setUiHints(Map<String, Object> uiHints) {
        this.uiHints = uiHints == null ? new LinkedHashMap<>() : uiHints;
    }

    public String getHealthCheckPath() {
        return healthCheckPath;
    }

    public void setHealthCheckPath(String healthCheckPath) {
        this.healthCheckPath = healthCheckPath;
    }

    public Integer getExpectedHeartbeatIntervalSeconds() {
        return expectedHeartbeatIntervalSeconds;
    }

    public void setExpectedHeartbeatIntervalSeconds(Integer expectedHeartbeatIntervalSeconds) {
        this.expectedHeartbeatIntervalSeconds = expectedHeartbeatIntervalSeconds;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }
}
