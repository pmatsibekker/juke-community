package org.juke.framework.proxy;

import org.juke.framework.config.JukeSpringContextHolder;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the consolidation-plan claim that only the interface-field {@code @Juke}
 * path is session-aware: with an active {@code JUKE_SESSION}, the interface path
 * routes through {@link SessionAwareReplayHandler}, while class-level {@code @Juke}
 * ({@link JukeClassInterceptor}) and the concrete-field path
 * ({@link TemplateMethodInterceptor}) ignore the session entirely and decide purely
 * from the global mode.
 *
 * <p>A session is made "active" via a mocked {@link ApplicationContext} (the same
 * lightweight pattern as {@code JukeFactoryNewInstanceTest}). The global mode is set
 * to {@code IGNORE} (passthrough): a session-aware path would replay from the
 * session regardless, but the concrete paths fall through to the real target —
 * proving they never consult the session.
 */
class ConcretePathSessionAwarenessTest {

    public interface IThing { String value(); }
    public static class ThingImpl implements IThing { public String value() { return "live"; } }
    public static class ConcreteThing { public String value() { return "live"; } }

    private String savedGlobal;

    @BeforeEach
    void setUp() {
        savedGlobal = JukeState.getGlobaljuke();
        JukeRuntimeHolder.reset();

        ApplicationContext appCtx = mock(ApplicationContext.class);
        JukeSessionContext sc = mock(JukeSessionContext.class);
        SessionRegistry reg = mock(SessionRegistry.class);
        when(sc.isPlaybackActive()).thenReturn(true);
        when(sc.getSessionId()).thenReturn("s1");
        when(reg.get("s1")).thenReturn(Optional.empty());
        when(appCtx.getBean(JukeSessionContext.class)).thenReturn(sc);
        when(appCtx.getBean(SessionRegistry.class)).thenReturn(reg);
        new JukeSpringContextHolder().setApplicationContext(appCtx);
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
        JukeRuntimeHolder.reset();
        new JukeSpringContextHolder().setApplicationContext(null);
    }

    @Test
    void interfaceFieldJuke_isSessionAware() {
        IThing proxy = new JukeFactory<IThing>()
                .newInstance(new ThingImpl(), IThing.class, JukeState.JUKE);
        assertTrue(proxy.toString().contains("JukeSessionReplayProxy"),
                "interface-field @Juke should route through SessionAwareReplayHandler when a session is active");
    }

    @Test
    void classLevelJuke_ignoresSession() {
        JukeState.setGlobaljuke(JukeState.IGNORE);   // global passthrough; session is active
        ConcreteThing proxy = JukeClassInterceptor.createProxy(new ConcreteThing(), ConcreteThing.class);
        assertEquals("live", proxy.value(),
                "class-level @Juke ignores the active session and uses only the global mode");
    }

    @Test
    void concreteFieldPath_ignoresSession() {
        JukeState.setGlobaljuke(JukeState.IGNORE);   // global passthrough; session is active
        IThing wrapped = TemplateRecordingWrapper.wrap(
                new ThingImpl(), "Thing", JukeState.JUKE, new String[0], IThing.class);
        assertEquals("live", wrapped.value(),
                "the concrete-field path ignores the active session and uses only the global mode");
    }
}
