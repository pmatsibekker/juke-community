package org.juke.framework.metadata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DataProgram} and {@link DataProgramSchedule}.
 */
class DataProgramScheduleTest {

    // ── DataProgram ───────────────────────────────────────────────────────

    @Test
    void dataProgram_defaultValues() {
        DataProgram dp = new DataProgram();
        assertEquals(0, dp.getIndex());
        assertEquals(0, dp.getLength());
        assertEquals("", dp.getContent());
    }

    @Test
    void dataProgram_setLength_setsIndexToOneWhenZero() {
        DataProgram dp = new DataProgram();
        dp.setLength(5);
        assertEquals(5, dp.getLength());
        assertEquals(1, dp.getIndex()); // auto-set to 1 when was 0
    }

    @Test
    void dataProgram_setLength_doesNotChangeNonZeroIndex() {
        DataProgram dp = new DataProgram();
        dp.setIndex(3);
        dp.setLength(5);
        assertEquals(3, dp.getIndex()); // unchanged
    }

    @Test
    void dataProgram_setContent() {
        DataProgram dp = new DataProgram();
        dp.setContent("hello");
        assertEquals("hello", dp.getContent());
    }

    // ── DataProgramSchedule ───────────────────────────────────────────────

    @Test
    void schedule_add_incrementsIndex() {
        DataProgramSchedule s = new DataProgramSchedule();
        int first = s.add("entry1");
        assertEquals(1, first);
        int second = s.add("entry1");
        assertEquals(2, second);
    }

    @Test
    void schedule_add_newEntry_startsAtOne() {
        DataProgramSchedule s = new DataProgramSchedule();
        assertEquals(1, s.add("newEntry"));
    }

    @Test
    void schedule_increment_doesNotExceedLength() {
        DataProgramSchedule s = new DataProgramSchedule();
        s.add("e");  // index=1, length=1
        s.add("e");  // index=2, length=2
        s.increment("e"); // index=3, but length=2 so clamps to 2
        assertEquals(2, s.current("e"));
    }

    @Test
    void schedule_current_freshEntry_returnsZero() {
        DataProgramSchedule s = new DataProgramSchedule();
        assertEquals(0, s.current("fresh"));
    }

    @Test
    void schedule_next_notExceedingLength() {
        DataProgramSchedule s = new DataProgramSchedule();
        s.add("x"); // length=1, index=1
        assertEquals(1, s.next("x")); // next is 2 but > length(1) → 1
    }

    @Test
    void schedule_size_returnsLength() {
        DataProgramSchedule s = new DataProgramSchedule();
        s.add("sz");
        s.add("sz");
        assertEquals(2, s.size("sz"));
    }

    @Test
    void schedule_getProgram_sameInstanceForSameKey() {
        DataProgramSchedule s = new DataProgramSchedule();
        DataProgram p1 = s.getProgram("key");
        DataProgram p2 = s.getProgram("key");
        assertSame(p1, p2);
    }

    @Test
    void schedule_snapshotPrograms_isUnmodifiable() {
        DataProgramSchedule s = new DataProgramSchedule();
        s.add("snap");
        var snapshot = s.snapshotPrograms();
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put("k", new DataProgram()));
    }

    @Test
    void schedule_getNextAvailable_returnsIndexedEntry() {
        DataProgramSchedule s = new DataProgramSchedule();
        s.add("base"); // length=1
        String first = s.getNextAvailable("base");
        assertEquals("base.1", first);
    }

    @Test
    void schedule_getNextAvailable_clampsToMax() {
        DataProgramSchedule s = new DataProgramSchedule();
        s.add("clamp"); // length=1, index=1
        s.getNextAvailable("clamp"); // uses idx=1, increments
        String second = s.getNextAvailable("clamp"); // idx would be 2 > length=1 → clamp to 1
        assertEquals("clamp.1", second);
    }

    @Test
    void schedule_getNextAvailable_noPriorAdd_usesOne() {
        DataProgramSchedule s = new DataProgramSchedule();
        // no add() called, so length=0, idx=0 → first call uses idx=1
        String result = s.getNextAvailable("noAdd");
        assertEquals("noAdd.1", result);
    }
}

