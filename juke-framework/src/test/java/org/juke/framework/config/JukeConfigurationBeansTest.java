package org.juke.framework.config;

import org.juke.framework.session.JukeCookieFilter;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.JukeSessionRegistry;
import org.juke.framework.session.SessionRegistry;
import org.juke.framework.spring.JukeBeanPostProcessor;
import org.juke.framework.spring.JukeTypeBeanPostProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JukeConfiguration} bean-factory methods (no full Spring
 * container needed for the static/instance factory methods).
 */
class JukeConfigurationBeansTest {

    // ── JukeConfiguration static factories ───────────────────────────────

    @Test
    void jukeBeanPostProcessor_isNotNull() {
        assertNotNull(JukeConfiguration.jukeBeanPostProcessor());
        assertInstanceOf(JukeBeanPostProcessor.class,
                JukeConfiguration.jukeBeanPostProcessor());
    }

    @Test
    void jukeTypeBeanPostProcessor_isNotNull() {
        assertNotNull(JukeConfiguration.jukeTypeBeanPostProcessor());
        assertInstanceOf(JukeTypeBeanPostProcessor.class,
                JukeConfiguration.jukeTypeBeanPostProcessor());
    }

    // ── JukeConfiguration instance factories ─────────────────────────────

    @Test
    void jukeSessionContext_returnsNewInstance() {
        JukeConfiguration cfg = new JukeConfiguration();
        JukeSessionContext ctx = cfg.jukeSessionContext();
        assertNotNull(ctx);
    }

    @Test
    void jukeSessionRegistry_returnsJukeSessionRegistryImpl() {
        JukeConfiguration cfg = new JukeConfiguration();
        SessionRegistry reg = cfg.jukeSessionRegistry();
        assertNotNull(reg);
        assertInstanceOf(JukeSessionRegistry.class, reg);
    }

    @Test
    void jukeSpringContextHolder_returnsNewInstance() {
        JukeConfiguration cfg = new JukeConfiguration();
        JukeSpringContextHolder holder = cfg.jukeSpringContextHolder();
        assertNotNull(holder);
    }

    @Test
    void jukeCookieFilter_returnsNonNull() {
        // jukeCookieFilter moved to JukeWebConfiguration so the bean is only
        // registered when spring-web is on the classpath. The factory method
        // itself still returns a non-null filter when called directly.
        ApplicationContext mockCtx = mock(ApplicationContext.class);
        SessionRegistry mockReg = mock(SessionRegistry.class);

        JukeWebConfiguration cfg = new JukeWebConfiguration();
        JukeCookieFilter filter = cfg.jukeCookieFilter(mockCtx, mockReg);
        assertNotNull(filter);
    }

    @Test
    void evictExpiredSessions_delegatesToRegistry() throws Exception {
        SessionRegistry mockReg = mock(SessionRegistry.class);
        JukeConfiguration cfg = new JukeConfiguration();

        // Inject the private sessionRegistry field
        Field f = JukeConfiguration.class.getDeclaredField("sessionRegistry");
        f.setAccessible(true);
        f.set(cfg, mockReg);

        cfg.evictExpiredSessions();
        verify(mockReg, times(1)).evictExpired();
    }
}

