package org.juke.remix.service;

import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.JukeSessionRegistry;
import org.juke.framework.storage.JukeStorage;
import org.juke.remix.service.dto.LastCallDto;
import org.juke.remix.service.dto.SessionStatusDto;
import org.juke.remix.service.dto.SessionsStatusResponse;
import org.juke.remix.service.dto.StepStatusDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RemixSessionsController}. Avoids Spring + Mockito: the
 * Boot 3.2 ASM cannot read Java 25 class files during context scanning, and
 * Mockito's inline mock maker fails to transform JVM 25 bytecode. Instead, a
 * small hand-rolled stub registry and DAO cover what we need.
 */
class RemixSessionsControllerTests {

    @Test
    void returnsEmptyPayloadWhenNoSessions() {
        RemixSessionsController controller = controllerWith(List.of());

        ResponseEntity<SessionsStatusResponse> response = controller.listSessions();
        assertEquals(200, response.getStatusCode().value());

        SessionsStatusResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(0, body.getActiveSessionCount());
        assertTrue(body.getSessions().isEmpty());
        assertNotNull(body.getGeneratedAt());
    }

    @Test
    void reportsStepProgressAndSummaryForActiveSession() {
        // Universe: three entries exercising all three statuses. A ".type."
        // sidecar and juke.json are included to verify JukeStateBuilder filters
        // them out.
        //   greeting: length 3, advanced once  -> in_progress (next step = 2)
        //   logout:   length 2, advanced twice -> completed  (next step = 2, clamp)
        //   login:    length 2, untouched      -> not_started
        Set<String> files = new LinkedHashSet<>();
        files.add("com.example.IGreetingsService.$greeting.1.json");
        files.add("com.example.IGreetingsService.$greeting.2.json");
        files.add("com.example.IGreetingsService.$greeting.3.json");
        files.add("com.example.IAuthService.$login.1.json");
        files.add("com.example.IAuthService.$login.2.json");
        files.add("com.example.IAuthService.$logout.1.json");
        files.add("com.example.IAuthService.$logout.2.json");
        files.add("com.example.IGreetingsService.$greeting.type.1.json");
        files.add("juke.json");

        StubStorage dao = new StubStorage(files);
        Instant startedAt = Instant.now().minusSeconds(30);
        JukeSessionEntry entry = new JukeSessionEntry(
                "session-abc", "login-happy-path", dao, startedAt);

        entry.getScheduleFor(DummyInterface.class).getNextAvailable(
                "com.example.IGreetingsService.$greeting");
        entry.getScheduleFor(DummyInterface.class).getNextAvailable(
                "com.example.IAuthService.$logout");
        entry.getScheduleFor(DummyInterface.class).getNextAvailable(
                "com.example.IAuthService.$logout");

        RemixSessionsController controller = controllerWith(List.of(entry));

        SessionsStatusResponse body = controller.listSessions().getBody();
        assertNotNull(body);
        assertEquals(1, body.getActiveSessionCount());

        SessionStatusDto session = body.getSessions().get(0);
        assertEquals("session-abc", session.getSessionId());
        assertEquals("login-happy-path", session.getTrack());
        assertEquals("replay", session.getMode());
        assertEquals(startedAt.toString(), session.getStartTime());
        assertTrue(session.getPlayTimeMs() >= 30_000L,
                "playTimeMs should reflect ~30s elapsed, got " + session.getPlayTimeMs());

        assertEquals(3, session.getSummary().getTotalSteps());
        assertEquals(1, session.getSummary().getCompletedSteps());
        assertEquals(1, session.getSummary().getInProgressSteps());
        assertEquals(1, session.getSummary().getNotStartedSteps());

        StepStatusDto greeting = findStep(session.getSteps(),
                "com.example.IGreetingsService.$greeting");
        assertEquals(3, greeting.getTotalLength());
        assertEquals(2, greeting.getCurrentIndex());
        assertEquals("in_progress", greeting.getStatus());

        StepStatusDto logout = findStep(session.getSteps(),
                "com.example.IAuthService.$logout");
        assertEquals(2, logout.getTotalLength());
        assertEquals(2, logout.getCurrentIndex());
        assertEquals("completed", logout.getStatus());

        StepStatusDto login = findStep(session.getSteps(),
                "com.example.IAuthService.$login");
        assertEquals(2, login.getTotalLength());
        assertEquals(0, login.getCurrentIndex());
        assertEquals("not_started", login.getStatus());

        // Coarse progress signal: greeting=2/3, logout=2/2, login=0/2 → 4/7 ≈ 57.14%.
        assertEquals(57.14, session.getPercentComplete(), 0.01);

        // No replay handler ran in this test, so lastCall stays null.
        assertNull(session.getLastCall());
    }

    @Test
    void surfacesLastCallWhenReplayHandlerHasResolvedAnEntry() {
        Set<String> files = new LinkedHashSet<>();
        files.add("com.example.IGreetingsService.$greeting.1.json");
        files.add("com.example.IGreetingsService.$greeting.2.json");
        files.add("com.example.IGreetingsService.$greeting.3.json");

        StubStorage dao = new StubStorage(files);
        JukeSessionEntry entry = new JukeSessionEntry(
                "session-xyz", "demo", dao, Instant.now().minusSeconds(5));

        // Mimic what SessionAwareReplayHandler does after resolving the
        // sequenced key for the 7th call to greeting().
        Instant resolvedAt = Instant.parse("2026-05-08T12:00:42Z");
        entry.recordCall("com.example.IGreetingsService.$greeting.7", resolvedAt);

        RemixSessionsController controller = controllerWith(List.of(entry));
        SessionStatusDto session = controller.listSessions().getBody().getSessions().get(0);

        LastCallDto last = session.getLastCall();
        assertNotNull(last);
        assertEquals("com.example.IGreetingsService.$greeting", last.getEntry());
        // Display strips the package and the leading '$' so the UI shows the
        // form a human actually wants to read.
        assertEquals("IGreetingsService.greeting.7", last.getDisplayName());
        assertEquals(7, last.getSequence());
        assertEquals(resolvedAt.toString(), last.getAt());
    }

    private static RemixSessionsController controllerWith(List<JukeSessionEntry> entries) {
        RemixSessionsController controller = new RemixSessionsController();
        ReflectionTestUtils.setField(controller, "registry", new StubRegistry(entries));
        return controller;
    }

    private static StepStatusDto findStep(List<StepStatusDto> steps, String key) {
        return steps.stream()
                .filter(s -> s.getEntry().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("step not found: " + key));
    }

    private interface DummyInterface {
    }

    /** Minimal registry stub: only {@link #snapshot()} is exercised. */
    private static final class StubRegistry extends JukeSessionRegistry {
        private final List<JukeSessionEntry> entries;

        StubRegistry(List<JukeSessionEntry> entries) {
            this.entries = entries;
        }

        @Override
        public Collection<JukeSessionEntry> snapshot() {
            return entries;
        }
    }

    /** Minimal DAO stub: only {@link #getFileNames()} is exercised. */
    private static final class StubStorage implements JukeStorage {
        private final Set<String> fileNames;

        StubStorage(Set<String> fileNames) {
            this.fileNames = fileNames;
        }

        @Override public Set<String> getFileNames() { return fileNames; }
        @Override public <T> T readFromFile(Class<T> c, String identifier) { return null; }
        @Override public <T> T readFromFileAsType(Class<?> interfaceClass, String identifier, Class<T> runtimeType) { return null; }
        @Override public boolean writeToFile(String identifier, String o) { return false; }
        @Override public void writeDirectEntry(String exactKey, String content) { }
        @Override public int getCurrentSequence(String identifier) { return 0; }
        @Override public String write() { return null; }
        @Override public String path() { return ""; }
        @Override public String asString(String identifier) { return null; }
    }
}
