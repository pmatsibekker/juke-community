package org.juke.framework.proxy;

import org.juke.framework.config.JukeSpringContextHolder;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Focused branch tests for {@link JukeFactory#newInstance(Object, Class, String)}.
 */
class JukeFactoryNewInstanceTest {

    interface IPingService {
        String ping(String input);
    }

    static class PingService implements IPingService {
        @Override
        public String ping(String input) {
            return "real-" + input;
        }
    }

    private String savedGlobal;

    @BeforeEach
    void setUp() {
        savedGlobal = JukeState.getGlobaljuke();
        JukeRuntimeHolder.reset();
        new JukeSpringContextHolder().setApplicationContext(null);
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
        JukeRuntimeHolder.reset();
        new JukeSpringContextHolder().setApplicationContext(null);
    }

    @Test
    void newInstance_nullJukeInput_returnsWrapped() {
        IPingService wrapped = new PingService();
        IPingService result = new JukeFactory<IPingService>()
                .newInstance(wrapped, IPingService.class, null);

        assertSame(wrapped, result);
    }

    @Test
    void newInstance_recordMode_returnsProxy() {
        JukeState.setGlobaljuke(JukeState.NONE);
        IPingService wrapped = new PingService();

        IPingService result = new JukeFactory<IPingService>()
                .newInstance(wrapped, IPingService.class, JukeState.RECORD);

        assertNotNull(result);
        assertTrue(Proxy.isProxyClass(result.getClass()));
    }

    @Test
    void newInstance_replayMode_cacheHit_returnsCachedProxy() {
        // Force class initialization first so static bootstrap cannot overwrite
        // the mode we set for this test.
        new JukeFactory<IPingService>();
        JukeState.setGlobaljuke(JukeState.REPLAY);

        IPingService wrapped = new PingService();
        IPingService cached = input -> "cached-" + input;
        JukeRuntimeHolder.current().proxyCache().put(IPingService.class, cached);

        IPingService result = new JukeFactory<IPingService>()
                .newInstance(wrapped, IPingService.class, JukeState.JUKE);

        assertSame(cached, result);
    }

    @Test
    void newInstance_sessionPlaybackActive_usesSessionAwareProxyPath() {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        JukeSessionContext sessionCtx = mock(JukeSessionContext.class);
        SessionRegistry registry = mock(SessionRegistry.class);

        when(sessionCtx.isPlaybackActive()).thenReturn(true);
        when(sessionCtx.getSessionId()).thenReturn("s1");
        when(registry.get("s1")).thenReturn(Optional.empty());
        when(appCtx.getBean(JukeSessionContext.class)).thenReturn(sessionCtx);
        when(appCtx.getBean(SessionRegistry.class)).thenReturn(registry);

        new JukeSpringContextHolder().setApplicationContext(appCtx);

        IPingService wrapped = new PingService();
        IPingService result = new JukeFactory<IPingService>()
                .newInstance(wrapped, IPingService.class, JukeState.REPLAY);

        assertNotNull(result);
        assertTrue(Proxy.isProxyClass(result.getClass()));
    }

    @Test
    void newInstance_sessionContextLookupFails_fallsBackToNormalFlow() {
        ApplicationContext appCtx = mock(ApplicationContext.class);
        when(appCtx.getBean(JukeSessionContext.class)).thenThrow(new RuntimeException("no request scope"));
        new JukeSpringContextHolder().setApplicationContext(appCtx);

        JukeState.setGlobaljuke(JukeState.NONE);
        IPingService wrapped = new PingService();

        IPingService result = new JukeFactory<IPingService>()
                .newInstance(wrapped, IPingService.class, null);

        assertSame(wrapped, result);
    }

}

