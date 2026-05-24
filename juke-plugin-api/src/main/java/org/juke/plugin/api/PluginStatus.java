package org.juke.plugin.api;

/**
 * Lifecycle states a plugin moves through from the perspective of the remix-side registry.
 *
 * <ul>
 *   <li>{@code ACTIVE} — registered, heartbeats observed within the last 60s, eligible for
 *       capability dispatch.</li>
 *   <li>{@code OFFLINE} — registered, but the heartbeat reaper has not seen a heartbeat for
 *       longer than the staleness threshold (60s by default). Excluded from capability
 *       dispatch. The registry retains the entry for diagnostics until it is replaced by a
 *       fresh registration or evicted entirely.</li>
 *   <li>{@code DEREGISTERED} — terminal state set by {@code POST /service/plugins/{id}/
 *       deregister}. The entry is removed from the registry; this enum value is used in event
 *       payloads, not stored.</li>
 * </ul>
 */
public enum PluginStatus {
    ACTIVE,
    OFFLINE,
    DEREGISTERED
}
