package org.juke.framework.config;

import org.juke.framework.session.JukeCookieFilter;
import org.juke.framework.session.SessionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Web-only Juke beans. Lives in its own {@code @Configuration} class
 * (separate from {@link JukeConfiguration}) so the class-level
 * {@code @ConditionalOnClass} guard can be evaluated via Spring's
 * ASM-based bytecode reader <em>before</em> the class is loaded
 * reflectively.
 *
 * <h2>Why a separate class is necessary</h2>
 *
 * <p>Non-web Spring Boot consumers of {@code juke-framework} (for
 * example {@code juke-sample-annotations}, which uses {@code @Juke} for
 * field wrapping but exposes no HTTP endpoints) do not have
 * {@code org.springframework.web.filter.OncePerRequestFilter} on the
 * classpath. If the {@code jukeCookieFilter} {@code @Bean} method lived
 * inside {@link JukeConfiguration}, Spring's
 * {@code @ConditionalOnMissingBean} evaluation would trigger
 * {@code ReflectionUtils.getDeclaredMethods(JukeConfiguration.class)},
 * which eagerly resolves every method's return type — including
 * {@link JukeCookieFilter}, which extends {@link OncePerRequestFilter}.
 * Without {@code spring-web} that load throws {@code NoClassDefFoundError}
 * and the entire {@code JukeConfiguration} is rejected with a
 * {@code BeanTypeDeductionException}, taking the non-web beans
 * (post-processors, session registry, scheduler) down with it.
 *
 * <p>An {@code @ConditionalOnClass} annotation <em>at @Bean method level</em>
 * does not help — by the time Spring evaluates that annotation it has
 * already failed to introspect the configuration class. Only a
 * class-level {@code @ConditionalOnClass}, evaluated against the
 * bytecode by {@code OnClassCondition} before the class is loaded, can
 * suppress the load entirely. That is what this class provides.
 *
 * <p>Registered via {@code META-INF/spring/.../AutoConfiguration.imports}
 * alongside {@link JukeConfiguration}.
 */
@Configuration
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
@ConditionalOnClass(OncePerRequestFilter.class)
public class JukeWebConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JukeCookieFilter jukeCookieFilter(ApplicationContext ctx, SessionRegistry registry) {
        return new JukeCookieFilter(ctx, registry);
    }
}
