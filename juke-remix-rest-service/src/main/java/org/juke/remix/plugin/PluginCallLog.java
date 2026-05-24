package org.juke.remix.plugin;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Bounded ring buffer of recent capability invocations remix made into plugins. Surfaced via
 * {@code GET /service/plugins/{id}/call-log} for the admin UI's diagnostics page.
 *
 * <p>Per §5.5, this is in-memory only. Persisting plugin call traces would balloon the H2
 * footprint and double-write everything that's already in the application log.
 *
 * <p>Capacity is per-plugin: 1000 entries default. Older entries roll off the tail when a new
 * one is appended at the head.
 */
@Component
public class PluginCallLog {

    /** Per-plugin capacity. Plan §5.5 suggests "e.g. 1000 entries". */
    public static final int DEFAULT_CAPACITY_PER_PLUGIN = 1000;

    private final int capacity;
    private final Map<String, Deque<Entry>> entriesByPlugin = new java.util.concurrent.ConcurrentHashMap<>();

    public PluginCallLog() {
        this(DEFAULT_CAPACITY_PER_PLUGIN);
    }

    public PluginCallLog(int capacity) {
        this.capacity = capacity;
    }

    public void record(Entry entry) {
        Deque<Entry> q = entriesByPlugin.computeIfAbsent(entry.pluginId, id -> new ConcurrentLinkedDeque<>());
        q.addFirst(entry);
        while (q.size() > capacity) {
            q.pollLast();
        }
    }

    public List<Entry> recentFor(String pluginId, int limit) {
        Deque<Entry> q = entriesByPlugin.get(pluginId);
        if (q == null) return Collections.emptyList();
        List<Entry> out = new ArrayList<>(Math.min(limit, q.size()));
        for (Entry e : q) {
            if (out.size() >= limit) break;
            out.add(e);
        }
        return out;
    }

    public void clearAll() {
        entriesByPlugin.clear();
    }

    public void clearFor(String pluginId) {
        entriesByPlugin.remove(pluginId);
    }

    /**
     * Single record. Kept thin — the admin-ui's call-log page renders these directly without
     * an extra mapping step. {@link #responseSummary} carries either a status code label or a
     * short error description; full payloads are not stored to keep the buffer cheap.
     */
    public static class Entry {
        public final String pluginId;
        public final String capability;
        public final String endpointKey;
        public final String url;
        public final Instant startedAt;
        public final long latencyMillis;
        public final boolean succeeded;
        public final Integer httpStatus;
        public final String responseSummary;
        public final Map<String, Object> tags;

        public Entry(String pluginId, String capability, String endpointKey, String url,
                     Instant startedAt, long latencyMillis, boolean succeeded,
                     Integer httpStatus, String responseSummary, Map<String, Object> tags) {
            this.pluginId = pluginId;
            this.capability = capability;
            this.endpointKey = endpointKey;
            this.url = url;
            this.startedAt = startedAt;
            this.latencyMillis = latencyMillis;
            this.succeeded = succeeded;
            this.httpStatus = httpStatus;
            this.responseSummary = responseSummary;
            this.tags = tags == null ? Collections.emptyMap() : new LinkedHashMap<>(tags);
        }
    }
}
