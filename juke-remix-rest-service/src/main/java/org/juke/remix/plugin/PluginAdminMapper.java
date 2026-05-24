package org.juke.remix.plugin;

import org.juke.plugin.api.PluginCapability;
import org.juke.plugin.api.registration.CapabilityDescriptor;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure mapping helpers — {@link RegisteredPlugin} → {@link PluginAdminDtos.PluginSummary} /
 * {@link PluginAdminDtos.PluginDetail}, and {@link PluginCallLog.Entry} → wire DTO. Kept
 * stateless so it can be invoked from controllers and tests without bean wiring.
 */
public final class PluginAdminMapper {

    private PluginAdminMapper() {}

    public static PluginAdminDtos.PluginSummary toSummary(RegisteredPlugin p, Clock clock) {
        long uptimeSeconds = Duration.between(p.getRegisteredAt(), clock.instant()).getSeconds();
        return new PluginAdminDtos.PluginSummary(
                p.getPluginId(),
                p.getDisplayName(),
                p.getVersion(),
                p.getBaseUrl(),
                p.getRegistrationId(),
                p.getStatus(),
                p.getRegisteredAt(),
                p.getLastHeartbeatAt(),
                Math.max(0, uptimeSeconds),
                p.getCapabilities(),
                p.getUiHints());
    }

    public static PluginAdminDtos.PluginDetail toDetail(RegisteredPlugin p,
                                                       List<PluginCallLog.Entry> recent,
                                                       Clock clock) {
        List<PluginAdminDtos.CallLogEntry> mapped = new ArrayList<>(recent.size());
        for (PluginCallLog.Entry e : recent) {
            mapped.add(new PluginAdminDtos.CallLogEntry(
                    e.capability,
                    e.endpointKey,
                    e.url,
                    e.startedAt,
                    e.latencyMillis,
                    e.succeeded,
                    e.httpStatus,
                    e.responseSummary));
        }
        return new PluginAdminDtos.PluginDetail(toSummary(p, clock), mapped);
    }

    public static PluginAdminDtos.CapabilitySummary toCapabilitySummary(
            Map<PluginCapability, List<RegisteredPlugin>> active) {
        Map<String, List<PluginAdminDtos.CapabilityPlugin>> wire = new LinkedHashMap<>();
        for (Map.Entry<PluginCapability, List<RegisteredPlugin>> e : active.entrySet()) {
            List<PluginAdminDtos.CapabilityPlugin> plugins = new ArrayList<>();
            for (RegisteredPlugin p : e.getValue()) {
                CapabilityDescriptor descriptor = p.getCapabilities().stream()
                        .filter(d -> d.getCapability() == e.getKey())
                        .findFirst()
                        .orElse(new CapabilityDescriptor(e.getKey()));
                plugins.add(new PluginAdminDtos.CapabilityPlugin(
                        p.getPluginId(), p.getDisplayName(), descriptor));
            }
            wire.put(e.getKey().name(), plugins);
        }
        return new PluginAdminDtos.CapabilitySummary(wire);
    }
}
