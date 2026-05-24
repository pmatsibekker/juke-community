package org.juke.framework.tuner;

import org.juke.framework.exception.TunerGeneratedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional tests for {@link ExceptionTunerTask} – covers execute with null exception
 * (no-op), execute with non-null exception, and Builder fluent methods.
 */
class ExceptionTunerTaskExtendedTest {

    @BeforeEach
    void setUp() {
        TunerTask.setParticipants(new java.util.HashMap<>());
        TunerTaskRegistry.tuners.clear();
    }

    @AfterEach
    void tearDown() {
        TunerTask.setParticipants(new java.util.HashMap<>());
        TunerTaskRegistry.tuners.clear();
    }

    @Test
    void execute_withNullException_doesNotThrow() throws Exception {
        ExceptionTunerTask task = new ExceptionTunerTask.Builder("sig-null-ex", "Exception")
                .exception(null)
                .build();
        // With null exception the execute method must not throw
        ProcessObject po = new ProcessObject();
        po.setSignature("sig-null-ex");
        assertDoesNotThrow(() -> task.execute(po));
    }

    @Test
    void execute_withException_throwsTunerGeneratedException() {
        IOException cause = new IOException("test-cause");
        ExceptionTunerTask task = new ExceptionTunerTask.Builder("sig-ex-task", "IOException")
                .exception(cause)
                .build();

        ProcessObject po = new ProcessObject();
        po.setSignature("sig-ex-task");

        TunerGeneratedException thrown = assertThrows(TunerGeneratedException.class,
                () -> task.execute(po));
        assertSame(cause, thrown.getWrappedException());
    }

    @Test
    void getDefaultTuner_returnsExceptionTunerTask() {
        TunerTask task = ExceptionTunerTask.getDefaultTuner("sig-default-ex");
        assertTrue(task instanceof ExceptionTunerTask);
    }

    @Test
    void builderFluentMethods_updateFields() {
        IOException ex = new IOException("fluent");
        ExceptionTunerTask task = new ExceptionTunerTask.Builder("initial-sig", "Exception")
                .sequencedItem("new-sig")
                .exception(ex)
                .build();
        assertEquals("new-sig", task.getSequencedItem());
        assertSame(ex, task.getException());
    }
}

