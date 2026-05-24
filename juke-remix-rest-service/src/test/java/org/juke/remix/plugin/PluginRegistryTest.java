package org.juke.remix.plugin;

import org.juke.plugin.api.PluginCapability;
import org.juke.plugin.api.PluginStatus;
import org.juke.plugin.api.registration.CapabilityDescriptor;
import org.juke.plugin.api.registration.PluginRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Phase 4.7 unit acceptance — token rotation + reaper, exercised at the registry level so the
 * test does not depend on Spring's web stack. The full controller test
 * ({@link PluginRegistryControllerIntegrationTest}) covers the wire side.
 *
 * <p>Token rotation: bullet 5 of the plan requires that re-registration issues a fresh
 * {@code pluginToken} and rejects the old one. We register, capture the first token, register
 * again with the same id, and assert the first token is no longer accepted on heartbeat.
 *
 * <p>Reaper: bullet 5 also requires that within 60s of heartbeats stopping, the plugin
 * disappears from the capability index. We drive a fixed clock past the 60s threshold and
 * assert {@link PluginRegistry#activeFor} excludes the now-OFFLINE plugin.
 */
class PluginRegistryTest {

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    @Test
    void register_rotatesTokenAndKeepsLatestEntry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-02T12:00:00Z"));
        PluginRegistry registry = new PluginRegistry(events, clock);

        RegisteredPlugin first = registry.register(sampleRegistration("p1"));
        String firstToken = first.getToken();
        assertThat(firstToken).isNotBlank();

        clock.advanceSeconds(5);
        RegisteredPlugin second = registry.register(sampleRegistration("p1"));
        assertThat(second.getToken())
                .as("re-registration must rotate the token")
                .isNotEqualTo(firstToken);

        // Heartbeat with old token is rejected.
        assertThatThrownBy(() -> registry.recordHeartbeat("p1", firstToken))
                .isInstanceOf(PluginRegistry.InvalidTokenException.class);

        // Heartbeat with new token works.
        registry.recordHeartbeat("p1", second.getToken());
        assertThat(registry.findById("p1")).get()
                .extracting(RegisteredPlugin::getStatus).isEqualTo(PluginStatus.ACTIVE);
    }

    @Test
    void reaper_marksOfflineWhenHeartbeatStopsForLongerThanThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-02T12:00:00Z"));
        PluginRegistry registry = new PluginRegistry(events, clock);

        RegisteredPlugin plugin = registry.register(sampleRegistration("late-bloomer"));
        assertThat(registry.activeFor(PluginCapability.USE_CASE_SUGGESTION))
                .extracting(RegisteredPlugin::getPluginId)
                .containsExactly("late-bloomer");

        // Last heartbeat is at registration time. Advance past 60s threshold.
        clock.advanceSeconds(61);

        List<String> went = registry.reapStale(60);
        assertThat(went).containsExactly("late-bloomer");
        assertThat(plugin.getStatus()).isEqualTo(PluginStatus.OFFLINE);
        assertThat(registry.activeFor(PluginCapability.USE_CASE_SUGGESTION))
                .as("OFFLINE plugin must drop out of the capability index")
                .isEmpty();
    }

    @Test
    void reaper_doesNotMarkOfflineIfHeartbeatJustObserved() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-02T12:00:00Z"));
        PluginRegistry registry = new PluginRegistry(events, clock);

        RegisteredPlugin plugin = registry.register(sampleRegistration("alive"));
        clock.advanceSeconds(45);
        registry.recordHeartbeat("alive", plugin.getToken());
        clock.advanceSeconds(45); // total 90s but heartbeat at 45s keeps it alive

        List<String> went = registry.reapStale(60);
        assertThat(went).isEmpty();
        assertThat(plugin.getStatus()).isEqualTo(PluginStatus.ACTIVE);
    }

    @Test
    void register_rejectsMissingFields() {
        PluginRegistry registry = new PluginRegistry(events);
        assertThatThrownBy(() -> registry.register(new PluginRegistration()))
                .isInstanceOf(IllegalArgumentException.class);

        PluginRegistration noBaseUrl = sampleRegistration("p1");
        noBaseUrl.setBaseUrl(null);
        assertThatThrownBy(() -> registry.register(noBaseUrl))
                .isInstanceOf(IllegalArgumentException.class);

        PluginRegistration noCaps = sampleRegistration("p1");
        noCaps.setCapabilities(List.of());
        assertThatThrownBy(() -> registry.register(noCaps))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PluginRegistration sampleRegistration(String pluginId) {
        PluginRegistration r = new PluginRegistration();
        r.setPluginId(pluginId);
        r.setDisplayName(pluginId + " (test)");
        r.setVersion("0.0.1");
        r.setBaseUrl("http://localhost:65000");
        r.setSharedSecret("test-secret");
        r.setCapabilities(List.of(new CapabilityDescriptor(PluginCapability.USE_CASE_SUGGESTION)));
        return r;
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void advanceSeconds(long seconds) { this.now = this.now.plusSeconds(seconds); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
