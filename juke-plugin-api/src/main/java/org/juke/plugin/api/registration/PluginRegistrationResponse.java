package org.juke.plugin.api.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Response body from {@code POST /service/plugins/register}. Remix issues a fresh
 * {@link #pluginToken} on every successful registration; previously issued tokens are
 * invalidated, so a re-registering plugin <em>must</em> stop sending the old token.
 *
 * <p>The token is a server-generated opaque string. The plugin includes it in the body of every
 * subsequent {@link Heartbeat}; remix returns 401 Unauthorized for stale tokens, which prompts
 * the plugin's lifecycle bean to re-register.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginRegistrationResponse {

    private String pluginId;
    private String registrationId;
    private String pluginToken;
    private Instant registeredAt;
    /** Expected heartbeat cadence remix recommends (typically 15s). Plugin SDKs honour this. */
    private Integer recommendedHeartbeatIntervalSeconds;
    /** Remix's HMAC secret for verifying inbound calls from this plugin. Used by the SDK. */
    private String remixSharedSecret;

    public PluginRegistrationResponse() {}

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getPluginToken() {
        return pluginToken;
    }

    public void setPluginToken(String pluginToken) {
        this.pluginToken = pluginToken;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Instant registeredAt) {
        this.registeredAt = registeredAt;
    }

    public Integer getRecommendedHeartbeatIntervalSeconds() {
        return recommendedHeartbeatIntervalSeconds;
    }

    public void setRecommendedHeartbeatIntervalSeconds(Integer recommendedHeartbeatIntervalSeconds) {
        this.recommendedHeartbeatIntervalSeconds = recommendedHeartbeatIntervalSeconds;
    }

    public String getRemixSharedSecret() {
        return remixSharedSecret;
    }

    public void setRemixSharedSecret(String remixSharedSecret) {
        this.remixSharedSecret = remixSharedSecret;
    }
}
