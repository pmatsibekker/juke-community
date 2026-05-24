package org.juke.framework.tuner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TunerTaskRegistry} – covers register, getTuner, contains.
 */
class TunerTaskRegistryTest {

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
    void getTuner_returnsRegisteredTuner() {
        // Build a DelayTunerTask which auto-registers via TunerTask.add()
        DelayTunerTask task = new DelayTunerTask.Builder("sig-registry-1", 0).build();

        String name = DelayTunerTask.class.getCanonicalName();
        TunerTask retrieved = TunerTaskRegistry.getTuner(name);
        assertNotNull(retrieved);
        assertSame(task, retrieved);
    }

    @Test
    void getTuner_returnsNullForUnregistered() {
        assertNull(TunerTaskRegistry.getTuner("org.juke.framework.tuner.NonExistentTuner"));
    }

    @Test
    void contains_trueForRegistered() {
        new DelayTunerTask.Builder("sig-registry-2", 0).build();
        assertTrue(TunerTaskRegistry.contains(DelayTunerTask.class.getCanonicalName()));
    }

    @Test
    void contains_falseForUnregistered() {
        assertFalse(TunerTaskRegistry.contains("org.juke.framework.tuner.NotRegistered"));
    }

    @Test
    void register_doesNotReplaceExistingEntry() {
        String name = DelayTunerTask.class.getCanonicalName();

        // Build a first task — this either registers it (first time) or is a no-op
        new DelayTunerTask.Builder("sig-dupe-a", 0).build();
        TunerTask storedAfterFirst = TunerTaskRegistry.getTuner(name);
        assertNotNull(storedAfterFirst);

        // Build a second task of the same class — register() must be a no-op
        new DelayTunerTask.Builder("sig-dupe-b", 0).build();
        TunerTask storedAfterSecond = TunerTaskRegistry.getTuner(name);

        // The stored instance must be unchanged
        assertSame(storedAfterFirst, storedAfterSecond,
                "register() on an already-registered class must be a no-op");
    }

    @Test
    void getTuners_isNonNull() {
        assertNotNull(TunerTaskRegistry.getTuners());
    }
}

