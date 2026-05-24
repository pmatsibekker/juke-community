package org.juke.plugin.sdk.lifecycle;

import org.juke.plugin.api.registration.Heartbeat;
import org.juke.plugin.sdk.config.PluginSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Periodic heartbeat sender. Cadence comes from {@link PluginSdkProperties#getHeartbeatIntervalSeconds()}
 * and is read at startup by Spring's scheduler — the {@code juke.plugin.heartbeat-interval-seconds}
 * key wins.
 *
 * <p>If remix returns 401 the cached token is treated as stale: this bean clears
 * {@link PluginRegistrationState} and asks the lifecycle bean to re-handshake. That covers the
 * acceptance scenario where a plugin restarts with the same {@code pluginId} and remix has
 * rotated tokens — the new instance simply re-registers and continues.
 */
public class HeartbeatPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(HeartbeatPublisher.class);

    private final PluginSdkProperties props;
    private final PluginRegistrationState state;
    private final PluginRegistrationLifecycle lifecycle;
    private final RestTemplate http;

    public HeartbeatPublisher(PluginSdkProperties props,
                              PluginRegistrationState state,
                              PluginRegistrationLifecycle lifecycle) {
        this.props = props;
        this.state = state;
        this.lifecycle = lifecycle;
        this.http = new RestTemplate();
    }

    @Scheduled(
            fixedRateString = "${juke.plugin.heartbeat-interval-seconds:15}000",
            initialDelayString = "${juke.plugin.heartbeat-initial-delay-seconds:5}000")
    public void tick() {
        if (!props.isEnabled()) {
            return;
        }
        if (!state.hasToken()) {
            // First registration may have failed (e.g. remix not yet up). Try again.
            lifecycle.register();
            if (!state.hasToken()) {
                return;
            }
        }
        Heartbeat body = new Heartbeat(state.getToken(), "ACTIVE");
        try {
            http.postForObject(
                    props.getRemixBaseUrl() + "/service/plugins/" + props.getPluginId() + "/heartbeat",
                    body,
                    Void.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            LOG.info("plugin {} heartbeat rejected (stale token) — re-registering", props.getPluginId());
            state.clear();
            lifecycle.register();
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            LOG.debug("plugin {} heartbeat failed: {}", props.getPluginId(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.debug("plugin {} heartbeat threw unexpectedly", props.getPluginId(), e);
        }
    }
}
