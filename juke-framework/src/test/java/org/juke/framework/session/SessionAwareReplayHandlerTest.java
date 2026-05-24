package org.juke.framework.session;

import org.juke.framework.proxy.SessionAwareReplayHandler;
import org.juke.framework.storage.JukeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SessionAwareReplayHandler}.
 */
class SessionAwareReplayHandlerTest {

    /** Simple test interface. */
    public interface IGreetingService {
        String greet(String name);
    }

    public interface ITypedGreetingService {
        Object greetAs(String name, Class<?> type);
    }

    /** Real service implementation used as fallback. */
    static class RealGreetingService implements IGreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name + "!";
        }
    }

    static class RealTypedGreetingService implements ITypedGreetingService {
        @Override
        public Object greetAs(String name, Class<?> type) {
            return "Hello, " + name + "!";
        }
    }

    private IGreetingService realService;
    private JukeSessionContext sessionContext;
    private SessionRegistry registry;
    private ITypedGreetingService typedService;

    @BeforeEach
    void setUp() {
        realService = new RealGreetingService();
        sessionContext = new JukeSessionContext();
        registry = mock(SessionRegistry.class);
        typedService = new RealTypedGreetingService();
    }

    @Test
    void expiredSession_fallsThroughToRealService() {
        sessionContext.setSessionId("expired-session");
        sessionContext.setTrackName("some-track");
        sessionContext.setPlaybackActive(true);

        // Registry returns empty for expired session
        when(registry.get("expired-session")).thenReturn(Optional.empty());

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        // Should fall through to real service
        String result = proxy.greet("World");
        assertEquals("Hello, World!", result);
    }

    @Test
    void proxy_implementsCorrectInterface() {
        sessionContext.setSessionId("test-session");
        sessionContext.setPlaybackActive(true);
        when(registry.get("test-session")).thenReturn(Optional.empty());

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        assertTrue(proxy instanceof IGreetingService);
        assertTrue(Proxy.isProxyClass(proxy.getClass()));
    }

    @Test
    void proxy_hashCode_returnsValue() {
        sessionContext.setSessionId("test-session");
        sessionContext.setPlaybackActive(true);
        when(registry.get("test-session")).thenReturn(Optional.empty());

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        // hashCode should return system identity hash
        assertDoesNotThrow(() -> proxy.hashCode());
    }

    @Test
    void proxy_equals_sameProxy_returnsTrue() {
        sessionContext.setSessionId("test-session");
        sessionContext.setPlaybackActive(true);
        when(registry.get("test-session")).thenReturn(Optional.empty());

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        // equals with same proxy
        assertTrue(proxy.equals(proxy));
    }

    @Test
    void proxy_equals_differentObject_returnsFalse() {
        sessionContext.setSessionId("test-session");
        sessionContext.setPlaybackActive(true);
        when(registry.get("test-session")).thenReturn(Optional.empty());

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        assertFalse(proxy.equals("not the proxy"));
    }

    @Test
    void proxy_toStringReturnsDescriptiveName() {
        sessionContext.setSessionId("test-session");
        sessionContext.setPlaybackActive(true);
        when(registry.get("test-session")).thenReturn(Optional.empty());

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        String str = proxy.toString();
        assertTrue(str.contains("JukeSessionReplayProxy"));
        assertTrue(str.contains("IGreetingService"));
    }

    @Test
    void activeSession_shortDiscriminated_usesReadFromFileAsType() {
        sessionContext.setSessionId("typed-session");
        sessionContext.setPlaybackActive(true);

        JukeStorage dao = mock(JukeStorage.class);
        when(dao.path()).thenReturn("mock-path");
        when(dao.getFileNames()).thenReturn(Set.of("ITypedGreetingService.greetAs@String.1.json"));
        when(dao.asString("ITypedGreetingService.greetAs@String.1")).thenReturn("\"recorded\"");
        when(dao.readFromFileAsType(ITypedGreetingService.class,
                "ITypedGreetingService.greetAs@String.1", String.class)).thenReturn("typed-result");

        JukeSessionEntry entry = new JukeSessionEntry("typed-session", "track", dao, Instant.now());
        when(registry.get("typed-session")).thenReturn(Optional.of(entry));

        ITypedGreetingService proxy = SessionAwareReplayHandler.newProxy(
                typedService, ITypedGreetingService.class, sessionContext, registry);

        Object result = proxy.greetAs("World", String.class);
        assertEquals("typed-result", result);
        verify(dao).readFromFileAsType(ITypedGreetingService.class,
                "ITypedGreetingService.greetAs@String.1", String.class);
        verify(dao, never()).readFromFile(ITypedGreetingService.class,
                "ITypedGreetingService.greetAs@String.1");
    }

    @Test
    void activeSession_shortPlainFallback_usesReadFromFile() {
        sessionContext.setSessionId("plain-session");
        sessionContext.setPlaybackActive(true);

        JukeStorage dao = mock(JukeStorage.class);
        when(dao.path()).thenReturn("mock-path");
        when(dao.getFileNames()).thenReturn(Set.of("IGreetingService.greet.1.json"));
        when(dao.asString("IGreetingService.greet.1")).thenReturn("\"plain\"");
        when(dao.readFromFile((Class) IGreetingService.class, "IGreetingService.greet.1"))
                .thenReturn("replayed-plain");

        JukeSessionEntry entry = new JukeSessionEntry("plain-session", "track", dao, Instant.now());
        when(registry.get("plain-session")).thenReturn(Optional.of(entry));

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        String result = proxy.greet("World");
        assertEquals("replayed-plain", result);
        verify(dao).readFromFile((Class) IGreetingService.class, "IGreetingService.greet.1");
    }

    @Test
    void activeSession_legacyFullNameFallback_usesLegacyIdentifier() {
        sessionContext.setSessionId("legacy-session");
        sessionContext.setPlaybackActive(true);

        JukeStorage dao = mock(JukeStorage.class);
        when(dao.path()).thenReturn("mock-path");
        when(dao.getFileNames()).thenReturn(Set.of("org.juke.framework.session.SessionAwareReplayHandlerTest$IGreetingService.$greet.1.json"));
        when(dao.asString("org.juke.framework.session.SessionAwareReplayHandlerTest$IGreetingService.$greet.1"))
                .thenReturn("\"legacy\"");
        when(dao.readFromFile((Class) IGreetingService.class,
                "org.juke.framework.session.SessionAwareReplayHandlerTest$IGreetingService.$greet.1"))
                .thenReturn("replayed-legacy");

        JukeSessionEntry entry = new JukeSessionEntry("legacy-session", "track", dao, Instant.now());
        when(registry.get("legacy-session")).thenReturn(Optional.of(entry));

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        String result = proxy.greet("World");
        assertEquals("replayed-legacy", result);
        verify(dao).readFromFile((Class) IGreetingService.class,
                "org.juke.framework.session.SessionAwareReplayHandlerTest$IGreetingService.$greet.1");
    }

    @Test
    void activeSession_daoException_returnsNullAfterInternalCatch() {
        sessionContext.setSessionId("error-session");
        sessionContext.setPlaybackActive(true);

        JukeStorage dao = mock(JukeStorage.class);
        when(dao.path()).thenReturn("mock-path");
        when(dao.getFileNames()).thenReturn(Set.of("IGreetingService.greet.1.json"));
        when(dao.asString("IGreetingService.greet.1")).thenThrow(new RuntimeException("boom"));

        JukeSessionEntry entry = new JukeSessionEntry("error-session", "track", dao, Instant.now());
        when(registry.get("error-session")).thenReturn(Optional.of(entry));

        IGreetingService proxy = SessionAwareReplayHandler.newProxy(
                realService, IGreetingService.class, sessionContext, registry);

        assertNull(proxy.greet("World"));
    }
}
