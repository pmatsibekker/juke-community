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
 * Proof-of-vulnerability test verifying that concrete paths (class-level @Juke and concrete-field path)
 * fail to route replay requests through active session context on the current codebase.
 */
public class ConcretePathSessionAwarenessProofTest {

    public interface IThing { String value(); }
    public static class ThingImpl implements IThing { public String value() { return "live"; } }
    public static class ConcreteThing { public String value() { return "live"; } }

    private String savedGlobal;

    @BeforeEach
    void setUp() {
        savedGlobal = JukeState.getGlobaljuke();
        JukeRuntimeHolder.reset();
        JukeState.setGlobaljuke(JukeState.IGNORE); // Passthrough by default
    }

    @AfterEach
    void tearDown() {
        JukeState.setGlobaljuke(savedGlobal);
        JukeRuntimeHolder.reset();
        new JukeSpringContextHolder().setApplicationContext(null);
    }

    @Test
    void proof_classLevelJuke_ignoresSession_originally() throws Exception {
        // 1. Arrange a mock session context & registry
        ApplicationContext appCtx = mock(ApplicationContext.class);
        JukeSessionContext sc = mock(JukeSessionContext.class);
        SessionRegistry reg = mock(SessionRegistry.class);
        JukeSessionEntry sessionEntry = mock(JukeSessionEntry.class);
        JukeStorage sessionDao = mock(JukeStorage.class);
        DataProgramSchedule schedule = mock(DataProgramSchedule.class);

        when(sc.isPlaybackActive()).thenReturn(true);
        when(sc.getSessionId()).thenReturn("session-abc");
        when(reg.get("session-abc")).thenReturn(Optional.of(sessionEntry));
        
        when(sessionEntry.getDao()).thenReturn(sessionDao);
        when(sessionEntry.getScheduleFor(any())).thenReturn(schedule);
        
        when(schedule.getNextAvailable(anyString())).thenReturn("ConcreteThing.value.1");
        when(sessionDao.asString("ConcreteThing.value.1")).thenReturn("\"mocked-from-session\"");
        when(sessionDao.readFromFile(any(), eq("ConcreteThing.value.1"))).thenReturn("mocked-from-session");

        when(appCtx.getBean(JukeSessionContext.class)).thenReturn(sc);
        when(appCtx.getBean(SessionRegistry.class)).thenReturn(reg);
        new JukeSpringContextHolder().setApplicationContext(appCtx);

        // 2. Act
        ConcreteThing proxy = JukeClassInterceptor.createProxy(new ConcreteThing(), ConcreteThing.class);
        String val = proxy.value();

        // 3. Assert - Expected to fail (return "live") on original codebase;
        // Should return "mocked-from-session" after the fix.
        assertEquals("mocked-from-session", val,
                "Expected class-level @Juke to return mocked-from-session via session DAO");
    }

    @Test
    void proof_concreteFieldPath_ignoresSession_originally() throws Exception {
        // 1. Arrange a mock session context & registry
        ApplicationContext appCtx = mock(ApplicationContext.class);
        JukeSessionContext sc = mock(JukeSessionContext.class);
        SessionRegistry reg = mock(SessionRegistry.class);
        JukeSessionEntry sessionEntry = mock(JukeSessionEntry.class);
        JukeStorage sessionDao = mock(JukeStorage.class);
        DataProgramSchedule schedule = mock(DataProgramSchedule.class);

        when(sc.isPlaybackActive()).thenReturn(true);
        when(sc.getSessionId()).thenReturn("session-abc");
        when(reg.get("session-abc")).thenReturn(Optional.of(sessionEntry));
        
        when(sessionEntry.getDao()).thenReturn(sessionDao);
        when(sessionEntry.getScheduleFor(any())).thenReturn(schedule);
        
        when(schedule.getNextAvailable(anyString())).thenReturn("Thing.value.1");
        when(sessionDao.asString("Thing.value.1")).thenReturn("\"mocked-from-session\"");
        when(sessionDao.asString("Thing.value.1.type")).thenReturn(String.class.getName());

        when(appCtx.getBean(JukeSessionContext.class)).thenReturn(sc);
        when(appCtx.getBean(SessionRegistry.class)).thenReturn(reg);
        new JukeSpringContextHolder().setApplicationContext(appCtx);

        // 2. Act
        IThing wrapped = TemplateRecordingWrapper.wrap(
                new ThingImpl(), "Thing", JukeState.JUKE, new String[0], IThing.class);
        String val = wrapped.value();

        // 3. Assert - Expected to fail (return "live") on original codebase;
        // Should return "mocked-from-session" after the fix.
        assertEquals("mocked-from-session", val,
                "Expected concrete-field path to return mocked-from-session via session DAO");
    }
}
