package org.juke.framework.session;

import org.juke.framework.storage.JukeStorage;
import org.juke.framework.metadata.DataProgramSchedule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link JukeSessionEntry}.
 */
class JukeSessionEntryTest {

    @TempDir
    Path tempDir;

    private File createTrackZip(String trackName) throws Exception {
        File zipFile = new File(tempDir.toFile(), trackName + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("ISampleService.getData.1.json");
            zos.putNextEntry(entry);
            zos.write("\"hello\"".getBytes());
            zos.closeEntry();
        }
        return zipFile;
    }

    @Test
    void constructor_getters_returnCorrectValues() {
        JukeStorage dao = mock(JukeStorage.class);
        Instant now = Instant.now();
        JukeSessionEntry entry = new JukeSessionEntry("sess-1", "track-A", dao, now);

        assertEquals("sess-1", entry.getSessionId());
        assertEquals("track-A", entry.getTrackName());
        assertSame(dao, entry.getDao());
        assertEquals(now, entry.getCreatedAt());
    }

    @Test
    void getSchedules_initiallyEmpty() {
        JukeStorage dao = mock(JukeStorage.class);
        JukeSessionEntry entry = new JukeSessionEntry("sess-1", "track-A", dao, Instant.now());
        Collection<DataProgramSchedule> schedules = entry.getSchedules();
        assertNotNull(schedules);
        assertTrue(schedules.isEmpty());
    }

    @Test
    void getScheduleFor_lazilyCachesSchedule() {
        JukeStorage dao = mock(JukeStorage.class);
        when(dao.getFileNames()).thenReturn(java.util.Set.of("ISampleService.getData.1.json"));
        JukeSessionEntry entry = new JukeSessionEntry("s", "t", dao, Instant.now());

        DataProgramSchedule s1 = entry.getScheduleFor(String.class);
        DataProgramSchedule s2 = entry.getScheduleFor(String.class);

        assertNotNull(s1);
        assertSame(s1, s2); // cached — same instance
        assertEquals(1, entry.getSchedules().size());
    }

    @Test
    void getScheduleFor_differentInterfaces_returnsDifferentSchedules() {
        JukeStorage dao = mock(JukeStorage.class);
        when(dao.getFileNames()).thenReturn(java.util.Set.of());
        JukeSessionEntry entry = new JukeSessionEntry("s", "t", dao, Instant.now());

        DataProgramSchedule s1 = entry.getScheduleFor(String.class);
        DataProgramSchedule s2 = entry.getScheduleFor(Integer.class);

        // Two different interface keys → two entries in the cache
        assertEquals(2, entry.getSchedules().size());
        assertNotSame(s1, s2);
    }
}

