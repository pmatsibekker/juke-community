package org.juke.plugin.sdk.lifecycle;

import org.juke.plugin.api.registration.CapabilityDescriptor;
import org.juke.plugin.api.registration.PluginRegistration;
import org.juke.plugin.api.registration.PluginRegistrationResponse;
import org.juke.plugin.sdk.annotation.PluginCapability;
import org.juke.plugin.sdk.config.PluginSdkProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walks every {@link PluginCapability}-annotated bean in the context, builds the
 * {@link PluginRegistration} payload, and POSTs it to remix on
 * {@link ApplicationReadyEvent}. The response — including the freshly minted plugin token —
 * is handed to {@link PluginRegistrationState} so {@link HeartbeatPublisher} can pick it up.
 *
 * <p>Failures during the initial handshake are logged but do not crash the plugin; the SDK
 * retries on every subsequent heartbeat tick by re-attempting registration if no token is
 * known. This keeps a plugin resilient to remix being slow to start.
 */
public class PluginRegistrationLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(PluginRegistrationLifecycle.class);

    private final PluginSdkProperties props;
    private final ApplicationContext ctx;
    private final RestTemplate http;
    private final PluginRegistrationState state;

    @Autowired
    public PluginRegistrationLifecycle(PluginSdkProperties props,
                                       ApplicationContext ctx,
                                       PluginRegistrationState state) {
        this.props = props;
        this.ctx = ctx;
        this.state = state;
        this.http = new RestTemplate();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (!props.isEnabled()) {
            LOG.info("juke.plugin.enabled=false — skipping plugin registration");
            return;
        }
        try {
            PluginRegistration body = buildRegistration();
            PluginRegistrationResponse response = http.postForObject(
                    props.getRemixBaseUrl() + "/service/plugins/register",
                    body,
                    PluginRegistrationResponse.class);
            if (response == null || response.getPluginToken() == null) {
                LOG.warn("plugin {} registration returned no token — will retry on next heartbeat", props.getPluginId());
                return;
            }
            state.setToken(response.getPluginToken());
            state.setRegistrationId(response.getRegistrationId());
            LOG.info("plugin {} registered with remix at {} (token issued, registrationId={})",
                    props.getPluginId(), props.getRemixBaseUrl(), response.getRegistrationId());
        } catch (HttpClientErrorException | HttpServerErrorException | ResourceAccessException e) {
            LOG.warn("plugin {} initial registration to {} failed: {} — will retry on heartbeat",
                    props.getPluginId(), props.getRemixBaseUrl(), e.getMessage());
        } catch (RuntimeException e) {
            LOG.warn("plugin {} initial registration failed unexpectedly", props.getPluginId(), e);
        }
    }

    PluginRegistration buildRegistration() {
        PluginRegistration body = new PluginRegistration();
        body.setPluginId(props.getPluginId());
        body.setDisplayName(props.getDisplayName());
        body.setVersion(props.getVersion());
        body.setBaseUrl(props.getBaseUrl());
        body.setHealthCheckPath(props.getHealthCheckPath());
        body.setExpectedHeartbeatIntervalSeconds(props.getHeartbeatIntervalSeconds());
        body.setSharedSecret(props.getSharedSecret());
        body.setUiHints(props.getUiHints());
        body.setCapabilities(discoverCapabilities());
        return body;
    }

    private List<CapabilityDescriptor> discoverCapabilities() {
        Map<String, Object> beans = ctx.getBeansWithAnnotation(PluginCapability.class);
        List<CapabilityDescriptor> out = new ArrayList<>();
        for (Object bean : beans.values()) {
            PluginCapability ann = AnnotationUtils.findAnnotation(bean.getClass(), PluginCapability.class);
            if (ann == null) {
                continue;
            }
            CapabilityDescriptor d = new CapabilityDescriptor(ann.value());
            out.add(d);
        }
        return out;
    }
}
