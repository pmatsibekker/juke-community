package org.juke.framework.harness;

import org.juke.framework.session.JukeSessionContext;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 — covers the no-op harness lifecycle. Real harnesses (e.g. Playwright) live in their
 * own modules and ship their own tests; here we just exercise the default that always loads.
 */
class NoOpUiHarnessTest {

    @Test
    void id_isStableConstant() {
        NoOpUiHarness harness = new NoOpUiHarness();
        assertEquals("none", harness.id());
        assertEquals(NoOpUiHarness.ID, harness.id());
    }

    @Test
    void describeArtefact_returnsNoArtefactDescriptor() {
        UiArtefactDescriptor desc = new NoOpUiHarness().describeArtefact();
        assertNotNull(desc);
        assertEquals("none.zip", desc.filename());
        assertEquals("application/zip", desc.mediaType());
        assertEquals("No UI harness", desc.displayName());
    }

    @Test
    void onSessionStart_isANoOp() {
        NoOpUiHarness harness = new NoOpUiHarness();
        JukeSessionContext ctx = new JukeSessionContext();
        ctx.setSessionId("session-A");
        ctx.setTrackName("track-A");
        // Should not throw; nothing to assert other than completion
        harness.onSessionStart(ctx);
    }

    @Test
    void onSessionStop_returnsEmptyOptional() {
        NoOpUiHarness harness = new NoOpUiHarness();
        JukeSessionContext ctx = new JukeSessionContext();
        ctx.setSessionId("session-B");
        Optional<byte[]> artefact = harness.onSessionStop(ctx);
        assertTrue(artefact.isEmpty());
    }

    @Test
    void canExecuteRecording_returnsFalse() {
        assertFalse(new NoOpUiHarness().canExecuteRecording());
    }

    @Test
    void executeUseCase_isANoOp() {
        NoOpUiHarness harness = new NoOpUiHarness();
        UseCaseExecutionPlan plan = new UseCaseExecutionPlan(
                UUID.randomUUID(), UUID.randomUUID(), "bundle-X", 1, null);
        // Should not throw; nothing to assert other than completion
        harness.executeUseCase(plan);
    }
}
