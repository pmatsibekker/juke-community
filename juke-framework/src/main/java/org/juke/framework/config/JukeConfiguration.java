package org.juke.framework.config;

import jakarta.annotation.PostConstruct;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.spring.JukeBeanPostProcessor;
import org.juke.framework.spring.JukeTypeBeanPostProcessor;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.JukeSessionRegistry;
import org.juke.framework.session.SessionRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for Juke annotation processing in Spring applications.
 * <p>
 * Registers two bean post-processors:
 * <ul>
 *     <li>{@link JukeBeanPostProcessor} — handles {@code @Juke} on <b>fields</b>
 *         (interface-based JDK proxies)</li>
 *     <li>{@link JukeTypeBeanPostProcessor} — handles {@code @Juke} on <b>classes</b>
 *         (CGLIB subclass proxies for concrete {@code @Component}/{@code @Service} beans)</li>
 * </ul>
 * <p>
 * Additionally registers session-related beans for cookie-based per-session
 * playback:
 * <ul>
 *     <li>{@link JukeSessionContext} — request-scoped bean holding session info</li>
 *     <li>{@link JukeSessionRegistry} — singleton registry of active sessions</li>
 *     <li>{@link JukeSpringContextHolder} — static accessor for ApplicationContext</li>
 * </ul>
 * <p>
 * The cookie-reading servlet filter ({@code JukeCookieFilter}) lives in
 * {@link JukeWebConfiguration}, which is gated by
 * {@code @ConditionalOnClass(OncePerRequestFilter)} so non-web consumers
 * of {@code juke-framework} boot cleanly without {@code spring-web} on
 * the classpath.
 */
@Configuration
@EnableScheduling
// Phase 5.B — host applications no longer need to component-scan
// org.juke.framework explicitly. NoOpUiHarness and HeadlessJukeRunner
// (the framework's @Component classes outside this package) get picked
// up here so the framework auto-config is self-sufficient.
@ComponentScan(basePackages = {"org.juke.framework.harness", "org.juke.framework.runner"})
//
// Master enablement toggle: every bean below is gated on
// {@code juke.enabled=true}. When the property is missing or false,
// none of these beans register — meaning:
//   * no {@code @Juke} fields get wrapped
//     (zero proxy overhead at runtime)
//   * the cookie filter / session registry / @Scheduled evictor are absent
//   * /service/* controllers (which apply the same condition) don't map
// Net effect: hosts opt into Juke per environment via YAML; the
// framework jar can sit on the production classpath without touching
// runtime behaviour unless someone explicitly turns it on.
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
public class JukeConfiguration {

    @Autowired
    @Lazy
    private SessionRegistry sessionRegistry;

    /**
     * Eagerly initialize the global Juke mode at Spring startup.
     *
     * <p>The framework's lazy-proxy architecture (see {@link org.juke.framework.spring.JukeBeanPostProcessor})
     * defers the first {@code JukeFactory} call — and therefore its static
     * initializer that would normally set the global mode to IGNORE — until
     * the first real service method call. This means
     * {@code JukeState.getGlobaljuke()} returns {@code null} when the REST
     * endpoints (e.g. {@code /service/record/start}) are hit before any
     * proxied method has been called, causing the null guard in the
     * controller to short-circuit with HTTP 500 "Unavailable Service".
     *
     * <p>Calling {@code setGlobaljuke} here, once during context
     * initialization, ensures the mode is always non-null by the time the
     * first HTTP request arrives. The mode defaults to {@code "ignore"}
     * (pass-through) when {@code juke.mode} is not set explicitly; callers
     * flip it to {@code "record"} or {@code "replay"} via the REST API.
     */
    @PostConstruct
    public void initializeJukeMode() {
        if (JukeState.getGlobaljuke() == null) {
            JukeState.setGlobaljuke(ConfigUtil.getJukeMode());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public static JukeBeanPostProcessor jukeBeanPostProcessor() {
        return new JukeBeanPostProcessor();
    }

    @Bean
    @ConditionalOnMissingBean
    public static JukeTypeBeanPostProcessor jukeTypeBeanPostProcessor() {
        return new JukeTypeBeanPostProcessor();
    }

    // --- Cookie-based per-session playback beans ---

    @Bean
    @ConditionalOnMissingBean
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    public JukeSessionContext jukeSessionContext() {
        return new JukeSessionContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionRegistry jukeSessionRegistry() {
        return new JukeSessionRegistry();
    }

    // The cookie filter bean lives in JukeWebConfiguration, which is gated by
    // @ConditionalOnClass(OncePerRequestFilter) at class level so non-web
    // consumers of juke-framework boot cleanly without spring-web on the
    // classpath. See JukeWebConfiguration for the rationale.

    @Bean
    @ConditionalOnMissingBean
    public JukeSpringContextHolder jukeSpringContextHolder() {
        return new JukeSpringContextHolder();
    }

    /**
     * Periodic cleanup of expired sessions (every 5 minutes).
     * Removes sessions older than 1 hour that were not explicitly stopped
     * (e.g., when a Playwright test crashes before calling /service/session/stop).
     */
    @Scheduled(fixedDelay = 300_000)
    public void evictExpiredSessions() {
        sessionRegistry.evictExpired();
    }
}