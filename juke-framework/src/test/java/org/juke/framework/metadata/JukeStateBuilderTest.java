package org.juke.framework.metadata;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeStateBuilder} and its inner {@link JukeStateBuilder.Builder}.
 * Uses the Set-based constructor to avoid file-system access.
 */
class JukeStateBuilderTest {

    @Test
    void build_withSimpleEntries_buildsSchedule() {
        Set<String> files = Set.of(
                "ISampleService.getData.1.json",
                "ISampleService.getData.2.json",
                "ISampleService.getData.3.json"
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertNotNull(schedule);
        assertEquals(3, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_ignoresTypeEntries() {
        Set<String> files = Set.of(
                "ISampleService.getData.1.json",
                "ISampleService.getData.type.1.json"  // should be skipped
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertEquals(1, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_ignoresArgsEntries() {
        Set<String> files = Set.of(
                "ISampleService.getData.1.json",
                "ISampleService.getData.args.1.json"  // should be skipped
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertEquals(1, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_ignoresJukeJsonMetadata() {
        Set<String> files = Set.of(
                "juke.json",
                "juke-mappings.json",
                "ISampleService.getData.1.json"
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertEquals(1, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_withRangeEntry_usesMaxIndex() {
        Set<String> files = Set.of("ISampleService.getData.[1-5].json");
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertEquals(5, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_emptyFiles_emptySchedule() {
        JukeStateBuilder builder = new JukeStateBuilder.Builder(Set.of()).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertNotNull(schedule);
        // No entries: size of a non-existent entry is 0
        assertEquals(0, schedule.size("anything"));
    }

    @Test
    void setFiles_replacesExistingFilter() {
        JukeStateBuilder.Builder b = new JukeStateBuilder.Builder(
                Set.of("ISampleService.getData.1.json"));
        b.setFiles(Set.of(
                "ISampleService.postData.1.json",
                "ISampleService.postData.2.json"
        ));
        JukeStateBuilder built = b.build();
        DataProgramSchedule schedule = built.getSchedule();
        assertEquals(2, schedule.size("ISampleService.postData"));
    }

    @Test
    void build_withNonNumericSuffixEntry_isIgnored() {
        // "someentry.json" has no .N.json pattern, should be filtered out
        Set<String> files = Set.of(
                "someentry.json",
                "ISampleService.getData.1.json"
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertEquals(1, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_withNonNumericDotSuffix_isIgnored() {
        // "ISampleService.getData.notnumber.json" should be filtered out
        Set<String> files = Set.of(
                "ISampleService.getData.notnumber.json",
                "ISampleService.getData.1.json"
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertEquals(1, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_entryEndingWithDotBeforeJson_isIgnoredByFilter() {
        // "service..json" → cleaned="service." → endsWith('.') = true
        // filter() condition (index > -1 && !cleaned.endsWith(".")) = false → not added
        Set<String> files = Set.of(
                "service..json",
                "ISampleService.getData.1.json"
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        // "service..json" is filtered out; only the valid entry is counted
        assertEquals(1, schedule.size("ISampleService.getData"));
        assertEquals(0, schedule.size("service."));
    }

    @Test
    void build_rangeEntryWithNonNumericBounds_isIgnoredBySplit() {
        // "[a-b]" passes filter() (has '[', ']', '-') but split() returns null
        // because "a" does not match \\d+ → entry not added to schedule
        Set<String> files = Set.of(
                "ISampleService.getData.[a-b].json",
                "ISampleService.getData.1.json"
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        // Only the valid numeric entry should contribute
        assertEquals(1, schedule.size("ISampleService.getData"));
    }

    @Test
    void build_rangeEntryWithSinglePart_isIgnoredBySplit() {
        // "[1].json" (no dash) fails filter() → not added, schedule stays empty
        // Verify that range without dash is silently dropped
        Set<String> files = Set.of(
                "ISampleService.getData.[1].json",
                "ISampleService.getData.1.json"
        );
        JukeStateBuilder builder = new JukeStateBuilder.Builder(files).build();
        DataProgramSchedule schedule = builder.getSchedule();
        assertEquals(1, schedule.size("ISampleService.getData"));
    }
}

