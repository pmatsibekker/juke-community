package org.juke.remix.plugin;

import org.juke.plugin.api.PluginCapability;
import org.juke.plugin.api.PluginStatus;
import org.juke.plugin.api.registration.CapabilityDescriptor;
import org.juke.plugin.api.registration.PluginRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton registry holding every currently-registered plugin in memory, plus a secondary
 * index keyed by capability for O(1) dispatch lookup. Per §5.4 nothing is persisted —
 * restarting remix forces every plugin to re-register, which is the design (no stale entries).
 *
 * <p>Token rotation: every successful {@link #register(PluginRegistration)} invalidates the
 * previously-issued token for the same {@code pluginId}. The freshly-issued token is what the
 * plugin must include on every {@link #recordHeartbeat heartbeat}; presenting an old one
 * yields {@link InvalidTokenException}, which the §8.8 controller maps to HTTP 401.
 */
@Component
public class PluginRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistry.class);

    private final Map<String, RegisteredPlugin> byId = new ConcurrentHashMap<>();
    private final Map<PluginCapability, List<String>> byCapability = new EnumMap<>(PluginCapability.class);
    private final Object capabilityIndexLock = new Object();

    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PluginRegistry(ApplicationEventPublisher events) {
        this(events, Clock.systemUTC());
    }

    /** Visible for tests — allows the reaper test to inject a fixed clock. */
    public PluginRegistry(ApplicationEventPublisher events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public RegisteredPlugin register(PluginRegistration registration) {
        if (registration == null || registration.getPluginId() == null
                || registration.getPluginId().isBlank()) {
            throw new IllegalArgumentException("pluginId is required");
        }
        if (registration.getBaseUrl() == null || registration.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (registration.getCapabilities() == null || registration.getCapabilities().isEmpty()) {
            throw new IllegalArgumentException("at least one capability must be declared");
        }

        String pluginId = registration.getPluginId();
        Instant now = clock.instant();
        RegisteredPlugin plugin = new RegisteredPlugin(
                registration,
                UUID.randomUUID().toString(),
                newToken(),
                generateRemixSharedSecret(),
                now);

        // Replace any prior entry — token rotation is the entire point.
        RegisteredPlugin previous = byId.put(pluginId, plugin);
        rebuildCapabilityIndex();
        if (previous != null) {
            LOG.info("plugin {} re-registered — previous token rotated out", pluginId);
        } else {
            LOG.info("plugin {} registered (capabilities={})", pluginId, plugin.capabilityCategories());
        }
        events.publishEvent(new PluginRegistryEvent(
                PluginRegistryEvent.Kind.REGISTERED, pluginId, PluginStatus.ACTIVE));
        return plugin;
    }

    public void recordHeartbeat(String pluginId, String token) {
        RegisteredPlugin p = byId.get(pluginId);
        if (p == null) {
            throw new UnknownPluginException(pluginId);
        }
        if (token == null || !token.equals(p.getToken())) {
            throw new InvalidTokenException(pluginId);
        }
        p.recordHeartbeat(clock.instant());
        events.publishEvent(new PluginRegistryEvent(
                PluginRegistryEvent.Kind.HEARTBEAT, pluginId, PluginStatus.ACTIVE));
    }

    public boolean deregister(String pluginId) {
        RegisteredPlugin removed = byId.remove(pluginId);
        if (removed == null) return false;
        rebuildCapabilityIndex();
        LOG.info("plugin {} deregistered", pluginId);
        events.publishEvent(new PluginRegistryEvent(
                PluginRegistryEvent.Kind.DEREGISTERED, pluginId, PluginStatus.DEREGISTERED));
        return true;
    }

    /**
     * Walk every registered plugin and mark anyone whose last heartbeat is older than
     * {@code now - stalenessThresholdSeconds} as OFFLINE. Returns the ids that were
     * transitioned this call. {@link PluginHeartbeatReaper} drives this on a schedule.
     */
    public List<String> reapStale(int stalenessThresholdSeconds) {
        Instant cutoff = clock.instant().minusSeconds(stalenessThresholdSeconds);
        List<String> wentOffline = new ArrayList<>();
        for (RegisteredPlugin p : byId.values()) {
            if (p.getStatus() == PluginStatus.ACTIVE && p.getLastHeartbeatAt().isBefore(cutoff)) {
                p.markOffline();
                wentOffline.add(p.getPluginId());
            }
        }
        if (!wentOffline.isEmpty()) {
            rebuildCapabilityIndex();
            for (String id : wentOffline) {
                LOG.info("plugin {} marked OFFLINE (stale heartbeat)", id);
                events.publishEvent(new PluginRegistryEvent(
                        PluginRegistryEvent.Kind.WENT_OFFLINE, id, PluginStatus.OFFLINE));
            }
        }
        return wentOffline;
    }

    public Optional<RegisteredPlugin> findById(String pluginId) {
        return Optional.ofNullable(byId.get(pluginId));
    }

    public Collection<RegisteredPlugin> all() {
        return Collections.unmodifiableCollection(byId.values());
    }

    /**
     * Capability-keyed view, ACTIVE plugins only. Used by both the dispatcher and the
     * {@code GET /service/plugins/capabilities} admin endpoint.
     */
    public Map<PluginCapability, List<RegisteredPlugin>> activeByCapability() {
        Map<PluginCapability, List<RegisteredPlugin>> out = new EnumMap<>(PluginCapability.class);
        synchronized (capabilityIndexLock) {
            for (Map.Entry<PluginCapability, List<String>> entry : byCapability.entrySet()) {
                List<RegisteredPlugin> live = new ArrayList<>();
                for (String id : entry.getValue()) {
                    RegisteredPlugin p = byId.get(id);
                    if (p != null && p.getStatus() == PluginStatus.ACTIVE) {
                        live.add(p);
                    }
                }
                if (!live.isEmpty()) {
                    out.put(entry.getKey(), live);
                }
            }
        }
        return out;
    }

    public List<RegisteredPlugin> activeFor(PluginCapability capability) {
        return activeByCapability().getOrDefault(capability, List.of());
    }

    /** Visible for tests — wipes the in-memory state without restarting the JVM. */
    public void resetForTests() {
        byId.clear();
        synchronized (capabilityIndexLock) {
            byCapability.clear();
        }
    }

    private void rebuildCapabilityIndex() {
        synchronized (capabilityIndexLock) {
            byCapability.clear();
            for (RegisteredPlugin p : byId.values()) {
                for (CapabilityDescriptor d : p.getCapabilities()) {
                    byCapability
                            .computeIfAbsent(d.getCapability(), k -> new ArrayList<>())
                            .add(p.getPluginId());
                }
            }
        }
    }

    private String newToken() {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String generateRemixSharedSecret() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** Returned to tests — UI only ever sees public-facing data. */
    public Map<String, Object> diagnosticSnapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("registered", byId.size());
        snap.put("byCapability", new LinkedHashMap<>(activeByCapability().keySet().stream()
                .collect(java.util.stream.Collectors.toMap(Enum::name,
                        c -> activeByCapability().get(c).size()))));
        return snap;
    }

    public static class UnknownPluginException extends RuntimeException {
        public final String pluginId;
        public UnknownPluginException(String pluginId) {
            super("unknown plugin: " + pluginId);
            this.pluginId = pluginId;
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public final String pluginId;
        public InvalidTokenException(String pluginId) {
            super("invalid or stale token for plugin: " + pluginId);
            this.pluginId = pluginId;
        }
    }
}
