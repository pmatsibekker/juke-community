package org.juke.remix.plugin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@code @Scheduled} task running every 30s by default — sweeps {@link PluginRegistry} and
 * marks any plugin whose last heartbeat is older than {@code juke.plugins.staleness-seconds}
 * (default 60) as {@code OFFLINE}. Per §5.4 / §5.7 (failure isolation), this is what makes a
 * crashed plugin disappear from the capability dispatch within the SLA.
 *
 * <p>Activation is gated by {@code juke.plugins.reaper.enabled=true} (default true). The
 * acceptance test for token rotation toggles it off so it can drive the reap step manually
 * via {@link #tickOnce()}.
 */
@Component
@ConditionalOnProperty(name = "juke.plugins.reaper.enabled", havingValue = "true", matchIfMissing = true)
public class PluginHeartbeatReaper {

    private static final Logger LOG = LoggerFactory.getLogger(PluginHeartbeatReaper.class);

    private final PluginRegistry registry;
    private final int stalenessSeconds;

    public PluginHeartbeatReaper(PluginRegistry registry,
                                 @Value("${juke.plugins.staleness-seconds:60}") int stalenessSeconds) {
        this.registry = registry;
        this.stalenessSeconds = stalenessSeconds;
    }

    @Scheduled(
            fixedDelayString = "${juke.plugins.reaper.poll-interval-ms:30000}",
            initialDelayString = "${juke.plugins.reaper.initial-delay-ms:5000}")
    public void poll() {
        try {
            tickOnce();
        } catch (RuntimeException e) {
            LOG.warn("plugin heartbeat reaper tick failed", e);
        }
    }

    public List<String> tickOnce() {
        return registry.reapStale(stalenessSeconds);
    }

    public int getStalenessSeconds() {
        return stalenessSeconds;
    }
}
