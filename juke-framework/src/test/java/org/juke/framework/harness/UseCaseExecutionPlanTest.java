package org.juke.framework.harness;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 — covers the placeholder shape consumed by {@link UiHarness#executeUseCase}.
 * Phase 4 will extend this class; these tests deliberately stick to the Phase 2 surface.
 */
class UseCaseExecutionPlanTest {

    @Test
    void constructor_storesAllFields_withUiSide() {
        UUID exec = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        UseCaseExecutionPlan plan = new UseCaseExecutionPlan(exec, run, "bundle-1", 3, 7);

        assertEquals(exec, plan.useCaseExecutionId());
        assertEquals(run, plan.testRunId());
        assertEquals("bundle-1", plan.bundleName());
        assertEquals(3, plan.jukeRecordingVersion());
        assertEquals(Integer.valueOf(7), plan.uiRecordingVersion());
        assertTrue(plan.hasUiSide());
    }

    @Test
    void hasUiSide_isFalseWhenUiVersionIsNull() {
        UseCaseExecutionPlan plan = new UseCaseExecutionPlan(
                UUID.randomUUID(), UUID.randomUUID(), "juke-only", 1, null);
        assertNull(plan.uiRecordingVersion());
        assertFalse(plan.hasUiSide());
    }

    @Test
    void constructor_rejectsNullUseCaseExecutionId() {
        assertThrows(NullPointerException.class, () -> new UseCaseExecutionPlan(
                null, UUID.randomUUID(), "b", 1, null));
    }

    @Test
    void constructor_rejectsNullTestRunId() {
        assertThrows(NullPointerException.class, () -> new UseCaseExecutionPlan(
                UUID.randomUUID(), null, "b", 1, null));
    }

    @Test
    void constructor_rejectsNullBundleName() {
        assertThrows(NullPointerException.class, () -> new UseCaseExecutionPlan(
                UUID.randomUUID(), UUID.randomUUID(), null, 1, null));
    }
}
