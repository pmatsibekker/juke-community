package org.juke.framework.session;

import org.juke.framework.annotation.JukeIgnorable;
import org.juke.framework.proxy.SessionAwareReplayHandler;
import org.juke.framework.storage.JukeStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for the {@code argListsMatch} bug: a randomly-changing field
 * marked {@link JukeIgnorable} must NOT be reported as an input deviation on
 * replay, while a genuine change to a non-ignored field still is.
 */
class JukeIgnorableReplayTest {

    public static class OrderRequest {
        public String sku;
        @JukeIgnorable
        public String confirmationNumber;

        public OrderRequest() {}
        public OrderRequest(String sku, String confirmationNumber) {
            this.sku = sku;
            this.confirmationNumber = confirmationNumber;
        }
    }

    public interface IOrderManagementSystem {
        String submitOrder(OrderRequest request);
    }

    static class RealOms implements IOrderManagementSystem {
        @Override public String submitOrder(OrderRequest request) { return "real"; }
    }

    private JukeSessionContext sessionContext;
    private SessionRegistry registry;

    /** Recorded args sidecar: sku=A1, confirmationNumber=REC-111. */
    private static final String RECORDED_ARGS = """
            {"method":"submitOrder",
             "parameterTypes":["org.juke.framework.session.JukeIgnorableReplayTest$OrderRequest"],
             "arguments":[{"sku":"A1","confirmationNumber":"REC-111"}]}""";

    @BeforeEach
    void setUp() {
        sessionContext = new JukeSessionContext();
        registry = mock(SessionRegistry.class);
    }

    private JukeSessionEntry primeSession(String sessionId) {
        sessionContext.setSessionId(sessionId);
        sessionContext.setPlaybackActive(true);

        JukeStorage dao = mock(JukeStorage.class);
        when(dao.path()).thenReturn("mock-path");
        when(dao.getFileNames()).thenReturn(Set.of("IOrderManagementSystem.submitOrder.1.json"));
        when(dao.asString("IOrderManagementSystem.submitOrder.1.args")).thenReturn(RECORDED_ARGS);
        when(dao.asString("IOrderManagementSystem.submitOrder.1")).thenReturn("\"ACK\"");
        when(dao.readFromFile((Class) IOrderManagementSystem.class,
                "IOrderManagementSystem.submitOrder.1")).thenReturn("ACK");

        JukeSessionEntry entry = new JukeSessionEntry(sessionId, "track", dao, Instant.now());
        when(registry.get(sessionId)).thenReturn(Optional.of(entry));
        return entry;
    }

    @Test
    void ignorableFieldChange_doesNotCountAsDeviation() {
        JukeSessionEntry entry = primeSession("ok-session");

        IOrderManagementSystem proxy = SessionAwareReplayHandler.newProxy(
                new RealOms(), IOrderManagementSystem.class, sessionContext, registry);

        // Different confirmationNumber than recorded — but it is @JukeIgnorable.
        Object result = proxy.submitOrder(new OrderRequest("A1", "LIVE-999"));
        assertEquals("ACK", result);

        List<JukeSessionEntry.CallRecord> history = entry.getCallHistory();
        assertEquals(1, history.size());
        assertTrue(history.get(0).inputMatched(),
                "Ignorable confirmationNumber must not be flagged as a deviation");
    }

    @Test
    void nonIgnoredFieldChange_isStillADeviation() {
        JukeSessionEntry entry = primeSession("dev-session");

        IOrderManagementSystem proxy = SessionAwareReplayHandler.newProxy(
                new RealOms(), IOrderManagementSystem.class, sessionContext, registry);

        // sku differs from the recording (B2 vs A1) and sku is NOT ignorable.
        proxy.submitOrder(new OrderRequest("B2", "LIVE-999"));

        List<JukeSessionEntry.CallRecord> history = entry.getCallHistory();
        assertEquals(1, history.size());
        assertFalse(history.get(0).inputMatched(),
                "A real change to a non-ignored field must be flagged as a deviation");
    }
}
