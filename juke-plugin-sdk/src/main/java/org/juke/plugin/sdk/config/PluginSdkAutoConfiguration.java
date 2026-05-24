package org.juke.plugin.sdk.config;

import org.juke.plugin.sdk.error.PluginSdkExceptionHandler;
import org.juke.plugin.sdk.lifecycle.HeartbeatPublisher;
import org.juke.plugin.sdk.lifecycle.PluginRegistrationLifecycle;
import org.juke.plugin.sdk.lifecycle.PluginRegistrationState;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auto-configuration for {@code juke-plugin-sdk}. Activated whenever a plugin app has the SDK
 * on the classpath; the {@code juke.plugin.enabled=false} switch is a no-op kill switch
 * (lifecycle and heartbeat short-circuit on the property — they still get instantiated so
 * tests can wire them).
 *
 * <p>Plugin auto-config registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "juke.plugin.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PluginSdkProperties.class)
@EnableScheduling
public class PluginSdkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PluginRegistrationState pluginRegistrationState() {
        return new PluginRegistrationState();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginRegistrationLifecycle pluginRegistrationLifecycle(
            PluginSdkProperties props,
            ApplicationContext ctx,
            PluginRegistrationState state) {
        return new PluginRegistrationLifecycle(props, ctx, state);
    }

    @Bean
    @ConditionalOnMissingBean
    public HeartbeatPublisher heartbeatPublisher(
            PluginSdkProperties props,
            PluginRegistrationState state,
            PluginRegistrationLifecycle lifecycle) {
        return new HeartbeatPublisher(props, state, lifecycle);
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginSdkExceptionHandler pluginSdkExceptionHandler() {
        return new PluginSdkExceptionHandler();
    }
}
