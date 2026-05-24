package org.juke.remix.plugin;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.juke.plugin.api.PluginStatus;
import org.juke.plugin.api.registration.CapabilityDescriptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire DTOs returned by the §8.8 admin endpoints. Kept in one file because every type here is
 * a thin record over the {@link RegisteredPlugin} state — splitting them across files trades
 * five lines for five separate files with frontmatter.
 *
 * <p>{@link PluginSummary} is what {@code GET /service/plugins} returns;
 * {@link PluginDetail} extends it with the recent call-log; {@link CapabilitySummary} is the
 * shape of {@code GET /service/plugins/capabilities}.
 */
public final class PluginAdminDtos {

    private PluginAdminDtos() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PluginSummary(
            String pluginId,
            String displayName,
            String version,
            String baseUrl,
            String registrationId,
            PluginStatus status,
            Instant registeredAt,
            Instant lastHeartbeatAt,
            long uptimeSeconds,
            List<CapabilityDescriptor> capabilities,
            Map<String, Object> uiHints) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PluginDetail(
            PluginSummary summary,
            List<CallLogEntry> recentCalls) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CallLogEntry(
            String capability,
            String endpointKey,
            String url,
            Instant startedAt,
            long latencyMillis,
            boolean succeeded,
            Integer httpStatus,
            String responseSummary) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CapabilityPlugin(
            String pluginId,
            String pluginName,
            CapabilityDescriptor descriptor) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CapabilitySummary(
            Map<String, List<CapabilityPlugin>> capabilities) {
    }
}
