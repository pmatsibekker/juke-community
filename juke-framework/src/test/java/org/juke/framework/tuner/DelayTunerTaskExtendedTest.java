package org.juke.framework.tuner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional unit tests for {@link DelayTunerTask} – covers execute with zero delay,
 * non-participant signature (ignored), and InterruptedException path.
 * Also covers {@link TunerTask} setParticipants.
 */
class DelayTunerTaskExtendedTest {

    @BeforeEach
    void clearState() {
        // Fully clear participants (setParticipants removes all keys;
        // clear() only empties the sets while keeping the keys).
        TunerTask.setParticipants(new java.util.HashMap<>());
        // Also clear the registry so stale instances from this class don't
        // pollute TunerTest, which registers fresh instances and expects them
        // to be the ones dispatched by JukeCommandProcessorChain.
        TunerTaskRegistry.tuners.clear();
    }

    @AfterEach
    void cleanUp() {
        TunerTask.setParticipants(new java.util.HashMap<>());
        TunerTaskRegistry.tuners.clear();
    }

    @Test
    void getDefaultTuner_returnsDelayTunerWithZeroDelay() {
        TunerTask task = DelayTunerTask.getDefaultTuner("sig-zero");
        assertTrue(task instanceof DelayTunerTask);
        assertEquals(0, ((DelayTunerTask) task).getDelay());
        assertEquals("sig-zero", ((DelayTunerTask) task).getSequencedItem());
    }

    @Test
    void execute_withZeroDelay_doesNotSleep() throws Exception {
        DelayTunerTask task = new DelayTunerTask.Builder("sig-no-sleep", 0).build();

        ProcessObject po = new ProcessObject();
        po.setSignature("sig-no-sleep");
        long start = System.currentTimeMillis();
        task.execute(po);
        assertTrue(System.currentTimeMillis() - start < 200,
                "Zero-delay execute should return quickly");
    }

    @Test
    void builderFluentMethods_workCorrectly() {
        DelayTunerTask task = new DelayTunerTask.Builder("initial", 100)
                .sequencedItem("updated-sig")
                .delay(999)
                .build();
        assertEquals("updated-sig", task.getSequencedItem());
        assertEquals(999, task.getDelay());
    }

    @Test
    void setParticipants_replacesExisting() {
        // Populate some state
        new DelayTunerTask.Builder("sig-sp-1", 0).build();
        assertFalse(TunerTask.getParticipants().isEmpty());

        // Replace with an empty map
        TunerTask.setParticipants(new java.util.HashMap<>());
        assertTrue(TunerTask.getParticipants().isEmpty());
    }

    @Test
    void setParticipants_withNull_clearsMap() {
        new DelayTunerTask.Builder("sig-sp-null", 0).build();
        TunerTask.setParticipants(null);
        assertTrue(TunerTask.getParticipants().isEmpty());
    }

    @Test
    void executeWith_nullTuner_throwsDescriptiveException() {
        DelayTunerTask task = new DelayTunerTask.Builder("sig-null-tuner", 0).build();
        task.tuner = null;  // corrupt the field to simulate misconfiguration

        ProcessObject po = new ProcessObject();
        po.setSignature("sig-null-tuner");
        Exception ex = assertThrows(Exception.class, () -> task.executeWith(po));
        assertTrue(ex.getMessage().contains("registered Tuner class"));
    }

    @Test
    void executeWith_matchingSignature_delegates() throws Exception {
        DelayTunerTask task = new DelayTunerTask.Builder("sig-delegate", 0).build();

        ProcessObject po = new ProcessObject();
        po.setSignature("sig-delegate");
        // Should complete without exception
        task.executeWith(po);
    }

    @Test
    void executeWith_nonMatchingSignature_skips() throws Exception {
        DelayTunerTask task = new DelayTunerTask.Builder("sig-exec-with", 5000).build();

        ProcessObject po = new ProcessObject();
        po.setSignature("sig-other");
        long start = System.currentTimeMillis();
        task.executeWith(po);
        assertTrue(System.currentTimeMillis() - start < 200);
    }
}

