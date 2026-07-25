package org.juke.framework.proxy;

import org.juke.framework.config.JukeSpringContextHolder;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.juke.framework.storage.JukeStorage;
import org.juke.framework.metadata.DataProgramSchedule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies all proxy paths honour an active {@code JUKE_SESSION} and replay from
 * the per-session DAO, even when the global mode is {@code IGNORE}.
 *
 * <p>A session is made "active" via a mocked {@link ApplicationContext} (the same
 * lightweight pattern as {@code JukeFactoryNewInstanceTest}). The global mode is set
 * to {@code IGNORE} (passthrough): session-aware paths must still replay from
 * the session track.
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
        JukeSessionEntry sessionEntry = mock(JukeSessionEntry.class);
        JukeStorage sessionDao = mock(JukeStorage.class);
        DataProgramSchedule schedule = mock(DataProgramSchedule.class);

        when(sc.isPlaybackActive()).thenReturn(true);
        when(sc.getSessionId()).thenReturn("s1");
        when(reg.get("s1")).thenReturn(Optional.of(sessionEntry));
        when(sessionEntry.getDao()).thenReturn(sessionDao);
        when(sessionEntry.getScheduleFor(any())).thenReturn(schedule);
        when(schedule.getNextAvailable(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0, String.class);
            return key != null && key.contains("ConcreteThing")
                    ? "ConcreteThing.value.1"
                    : "Thing.value.1";
        });
        when(sessionDao.readFromFile(any(), eq("ConcreteThing.value.1"))).thenReturn("mocked-from-session");
        when(sessionDao.asString("Thing.value.1")).thenReturn("\"mocked-from-session\"");
        when(sessionDao.asString("Thing.value.1.type")).thenReturn(String.class.getName());
        doNothing().when(sessionEntry).recordCall(anyString(), any());

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
    void classLevelJuke_replaysFromSessionEvenWhenGlobalIgnore() {
        JukeState.setGlobaljuke(JukeState.IGNORE);   // global passthrough; session is active
        ConcreteThing proxy = JukeClassInterceptor.createProxy(new ConcreteThing(), ConcreteThing.class);
        assertEquals("mocked-from-session", proxy.value(),
                "class-level @Juke should replay from the active session DAO");
    }

    @Test
    void concreteFieldPath_replaysFromSessionEvenWhenGlobalIgnore() {
        JukeState.setGlobaljuke(JukeState.IGNORE);   // global passthrough; session is active
        IThing wrapped = TemplateRecordingWrapper.wrap(
                new ThingImpl(), "Thing", JukeState.JUKE, new String[0], IThing.class);
        assertEquals("mocked-from-session", wrapped.value(),
                "the concrete-field path should replay from the active session DAO");
    }
}
