package org.juke.plugin.sdk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration sink for the {@code juke.plugin.*} keys. Plugin authors set these in their
 * own {@code application.yml}:
 *
 * <pre>{@code
 *   juke:
 *     plugin:
 *       enabled: true
 *       plugin-id: juke-plugin-agent-anthropic
 *       display-name: Anthropic AI Agent
 *       version: 1.0.0
 *       base-url: http://localhost:8090   # plugin's own externally-reachable URL
 *       remix-base-url: http://localhost:8080
 *       heartbeat-interval-seconds: 15
 *       shared-secret: ${JUKE_PLUGIN_SHARED_SECRET:}
 *       ui-hints:
 *         primaryActionLabel: Generate Suggestions
 *         iconName: robot
 * }</pre>
 *
 * <p>{@link #remixBaseUrl} is the URL the lifecycle bean POSTs the registration to;
 * {@link #baseUrl} is what the plugin advertises to remix as its own callback URL.
 */
@ConfigurationProperties(prefix = "juke.plugin")
public class PluginSdkProperties {

    /** Master switch — when false, no registration / heartbeat happens. Default true. */
    private boolean enabled = true;

    private String pluginId;
    private String displayName;
    private String version;
    private String baseUrl;
    private String remixBaseUrl = "http://localhost:8080";

    /** Plugin's heartbeat cadence. Default 15s, matching §5.3. */
    private int heartbeatIntervalSeconds = 15;

    /** Initial delay before the first heartbeat, measured from registration. */
    private int heartbeatInitialDelaySeconds = 5;

    /** Healthcheck path advertised to remix. Default {@code /actuator/health}. */
    private String healthCheckPath = "/actuator/health";

    private String sharedSecret;

    private Map<String, Object> uiHints = new LinkedHashMap<>();

    /** Optional list of capability overrides — usually left empty (annotations win). */
    private List<String> capabilityOverrides = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getRemixBaseUrl() { return remixBaseUrl; }
    public void setRemixBaseUrl(String remixBaseUrl) { this.remixBaseUrl = remixBaseUrl; }
    public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) { this.heartbeatIntervalSeconds = heartbeatIntervalSeconds; }
    public int getHeartbeatInitialDelaySeconds() { return heartbeatInitialDelaySeconds; }
    public void setHeartbeatInitialDelaySeconds(int heartbeatInitialDelaySeconds) { this.heartbeatInitialDelaySeconds = heartbeatInitialDelaySeconds; }
    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }
    public String getSharedSecret() { return sharedSecret; }
    public void setSharedSecret(String sharedSecret) { this.sharedSecret = sharedSecret; }
    public Map<String, Object> getUiHints() { return uiHints; }
    public void setUiHints(Map<String, Object> uiHints) { this.uiHints = uiHints == null ? new LinkedHashMap<>() : uiHints; }
    public List<String> getCapabilityOverrides() { return capabilityOverrides; }
    public void setCapabilityOverrides(List<String> capabilityOverrides) { this.capabilityOverrides = capabilityOverrides == null ? new ArrayList<>() : capabilityOverrides; }
}
