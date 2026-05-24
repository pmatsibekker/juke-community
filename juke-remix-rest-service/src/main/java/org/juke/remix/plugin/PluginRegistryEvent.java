package org.juke.remix.plugin;

import org.juke.plugin.api.PluginStatus;

/**
 * Lightweight event published by {@link PluginRegistry} when a plugin transitions between
 * states. Listeners include {@link PluginCallLog} cleaners and (in later phases) admin-ui
 * push channels.
 *
 * <p>Plain Java record so listeners can pattern-match the {@link Kind} cleanly without dragging
 * in a Spring {@code ApplicationEvent} hierarchy.
 */
public record PluginRegistryEvent(Kind kind, String pluginId, PluginStatus newStatus) {

    public enum Kind {
        REGISTERED,
        HEARTBEAT,
        WENT_OFFLINE,
        DEREGISTERED
    }
}
