package org.juke.remix.plugin;

import org.juke.plugin.api.PluginCapability;
import org.juke.plugin.api.PluginStatus;
import org.juke.plugin.api.registration.CapabilityDescriptor;
import org.juke.plugin.api.registration.PluginRegistration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * In-memory snapshot of a registered plugin held by {@link PluginRegistry}. Mutable state
 * (status, last heartbeat, last call) is exposed through small getter/setter methods so the
 * reaper and the call-log writer can update it without leaking the underlying data structure.
 *
 * <p>Not persisted — every restart of remix triggers a re-registration handshake from each
 * plugin, per §5.4. That is the design: stale registry entries pointing at dead URLs are a
 * worse failure mode than the brief startup gap.
 */
public class RegisteredPlugin {

    private final String pluginId;
    private final String displayName;
    private final String version;
    private final String baseUrl;
    private final String registrationId;
    private final List<CapabilityDescriptor> capabilities;
    private final Map<String, Object> uiHints;
    private final String healthCheckPath;
    private final Integer expectedHeartbeatIntervalSeconds;
    private final String pluginSharedSecret;
    private final String remixSharedSecret;
    private final Instant registeredAt;

    private final AtomicReference<String> token;
    private final AtomicReference<Instant> lastHeartbeatAt;
    private final AtomicReference<PluginStatus> status;

    public RegisteredPlugin(PluginRegistration registration,
                            String registrationId,
                            String token,
                            String remixSharedSecret,
                            Instant registeredAt) {
        this.pluginId = registration.getPluginId();
        this.displayName = registration.getDisplayName();
        this.version = registration.getVersion();
        this.baseUrl = stripTrailingSlash(registration.getBaseUrl());
        this.registrationId = registrationId;
        this.capabilities = new ArrayList<>(registration.getCapabilities());
        this.uiHints = new LinkedHashMap<>(registration.getUiHints());
        this.healthCheckPath = registration.getHealthCheckPath();
        this.expectedHeartbeatIntervalSeconds = registration.getExpectedHeartbeatIntervalSeconds();
        this.pluginSharedSecret = registration.getSharedSecret();
        this.remixSharedSecret = remixSharedSecret;
        this.registeredAt = registeredAt;
        this.token = new AtomicReference<>(token);
        this.lastHeartbeatAt = new AtomicReference<>(registeredAt);
        this.status = new AtomicReference<>(PluginStatus.ACTIVE);
    }

    private static String stripTrailingSlash(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public String getPluginId() { return pluginId; }
    public String getDisplayName() { return displayName; }
    public String getVersion() { return version; }
    public String getBaseUrl() { return baseUrl; }
    public String getRegistrationId() { return registrationId; }
    public List<CapabilityDescriptor> getCapabilities() { return capabilities; }
    public Map<String, Object> getUiHints() { return uiHints; }
    public String getHealthCheckPath() { return healthCheckPath; }
    public Integer getExpectedHeartbeatIntervalSeconds() { return expectedHeartbeatIntervalSeconds; }
    public String getPluginSharedSecret() { return pluginSharedSecret; }
    public String getRemixSharedSecret() { return remixSharedSecret; }
    public Instant getRegisteredAt() { return registeredAt; }
    public String getToken() { return token.get(); }
    public Instant getLastHeartbeatAt() { return lastHeartbeatAt.get(); }
    public PluginStatus getStatus() { return status.get(); }

    public void recordHeartbeat(Instant at) {
        this.lastHeartbeatAt.set(at);
        this.status.set(PluginStatus.ACTIVE);
    }

    public void markOffline() {
        this.status.set(PluginStatus.OFFLINE);
    }

    public Set<PluginCapability> capabilityCategories() {
        return capabilities.stream()
                .map(CapabilityDescriptor::getCapability)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
