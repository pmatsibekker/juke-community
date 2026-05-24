package org.juke.framework.proxy;

import org.juke.framework.exception.JukeStorageException;
import org.juke.framework.runtime.JukeMode;
import org.juke.framework.runtime.JukeRuntime;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.storage.JukeStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Branch-focused tests for ReplayHandler invoke-path identifier resolution.
 */
class ReplayHandlerBranchTest {

    interface IReplaySvc {
        String greet(String name);
    }

    interface ITypedReplaySvc {
        Object greetAs(String name, Class<?> type);
    }

    static class ReplaySvc implements IReplaySvc {
        @Override
        public String greet(String name) {
            return "real-" + name;
        }
    }

    static class TypedReplaySvc implements ITypedReplaySvc {
        @Override
        public Object greetAs(String name, Class<?> type) {
            return "real-" + name;
        }
    }

    private String savedGlobal;
    private JukeRuntime previousRuntime;

    @BeforeEach
    void setUp() {
        savedGlobal = JukeState.getGlobaljuke();
        previousRuntime = JukeRuntimeHolder.current();
        ReplayHandler.getReplayHandlerCache().clear();
    }

    @AfterEach
    void tearDown() {
        ReplayHandler.getReplayHandlerCache().clear();
        JukeRuntimeHolder.set(previousRuntime);
        JukeState.setGlobaljuke(savedGlobal);
    }

    @Test
    void invoke_shortPlainEntry_isPreferred() {
        JukeStorage dao = mockDao(Set.of("IReplaySvc.greet.1.json"));
        when(dao.readFromFile((Class) IReplaySvc.class, "IReplaySvc.greet.1")).thenReturn("replay-short");
        installRuntime(dao);

        IReplaySvc proxy = new ReplayHandler<>(new ReplaySvc(), IReplaySvc.class)
                .newInstance(IReplaySvc.class);

        assertEquals("replay-short", proxy.greet("x"));
        verify(dao).readFromFile((Class) IReplaySvc.class, "IReplaySvc.greet.1");
    }

    @Test
    void invoke_legacyFullEntry_isFallback() {
        String legacy = IReplaySvc.class.getName() + ".$greet.1";

        JukeStorage dao = mockDao(Set.of(legacy + ".json"));
        when(dao.readFromFile((Class) IReplaySvc.class, legacy)).thenReturn("replay-legacy");
        installRuntime(dao);

        IReplaySvc proxy = new ReplayHandler<>(new ReplaySvc(), IReplaySvc.class)
                .newInstance(IReplaySvc.class);

        assertEquals("replay-legacy", proxy.greet("x"));
        verify(dao).readFromFile((Class) IReplaySvc.class, legacy);
    }

    @Test
    void invoke_shortDiscriminatedEntry_usesReadFromFileAsType() {
        String key = "ITypedReplaySvc.greetAs@String.1";

        JukeStorage dao = mockDao(Set.of(key + ".json"));
        when(dao.readFromFileAsType(ITypedReplaySvc.class, key, String.class))
                .thenReturn("typed-short");
        installRuntime(dao);

        ITypedReplaySvc proxy = new ReplayHandler<>(new TypedReplaySvc(), ITypedReplaySvc.class)
                .newInstance(ITypedReplaySvc.class);

        assertEquals("typed-short", proxy.greetAs("x", String.class));
        verify(dao).readFromFileAsType(ITypedReplaySvc.class, key, String.class);
    }

    @Test
    void invoke_legacyDiscriminatedEntry_isFallbackWhenShortMissing() {
        String key = ITypedReplaySvc.class.getName() + ".$greetAs@java.lang.String.1";

        JukeStorage dao = mockDao(Set.of(key + ".json"));
        when(dao.readFromFileAsType(ITypedReplaySvc.class, key, String.class))
                .thenReturn("typed-legacy");
        installRuntime(dao);

        ITypedReplaySvc proxy = new ReplayHandler<>(new TypedReplaySvc(), ITypedReplaySvc.class)
                .newInstance(ITypedReplaySvc.class);

        assertEquals("typed-legacy", proxy.greetAs("x", String.class));
        verify(dao).readFromFileAsType(ITypedReplaySvc.class, key, String.class);
    }

    @Test
    void invoke_objectMethods_areHandledWithoutReplay() {
        JukeStorage dao = mockDao(Set.of());
        installRuntime(dao);

        IReplaySvc proxy = new ReplayHandler<>(new ReplaySvc(), IReplaySvc.class)
                .newInstance(IReplaySvc.class);

        assertTrue(proxy.toString().contains("JukeReplayProxy"));
        assertNotEquals(0, proxy.hashCode());
        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals("other"));
    }

    private static void installRuntime(JukeStorage dao) {
        JukeRuntime runtime = JukeRuntime.builder()
                .mode(JukeMode.REPLAY)
                .storage(dao)
                .build();
        JukeRuntimeHolder.set(runtime);
        JukeState.setGlobaljuke(JukeState.REPLAY);
    }

    private static JukeStorage mockDao(Set<String> names) {
        JukeStorage dao = mock(JukeStorage.class);
        when(dao.path()).thenReturn("mock-path");
        when(dao.getFileNames()).thenReturn(names);
        when(dao.asString(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            if (key.endsWith(".args")) {
                throw new JukeStorageException("missing args sidecar");
            }
            return "\"json\"";
        });
        return dao;
    }
}

