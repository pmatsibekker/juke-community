package org.juke.framework.session;

import org.juke.framework.config.ConfigUtil;
import org.juke.framework.exception.JukeAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JukeSessionRegistry}.
 */
class JukeSessionRegistryTest {

    private JukeSessionRegistry registry;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new JukeSessionRegistry();
        // Point the Juke path to our temp directory
        System.setProperty("juke.path", tempDir.toString());
    }

    /**
     * Creates a minimal valid ZIP file for a given track name.
     */
    private void createTrackZip(String trackName) throws Exception {
        File zipFile = new File(tempDir.toFile(), trackName + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // Add a minimal juke.json entry
            ZipEntry entry = new ZipEntry("juke.json");
            zos.putNextEntry(entry);
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
    }

    @Test
    void create_withValidTrack_returnsEntryWithNonNullDAO() throws Exception {
        createTrackZip("test-track");

        JukeSessionEntry entry = registry.create("test-track");

        assertNotNull(entry);
        assertNotNull(entry.getSessionId());
        assertFalse(entry.getSessionId().isEmpty());
        assertEquals("test-track", entry.getTrackName());
        assertNotNull(entry.getDao());
        assertNotNull(entry.getCreatedAt());
    }

    @Test
    void create_withNonExistentZip_throwsJukeAccessException() {
        assertThrows(JukeAccessException.class, () -> {
            registry.create("nonexistent-track");
        });
    }

    @Test
    void isValid_returnsTrueForKnownSession() throws Exception {
        createTrackZip("my-track");
        JukeSessionEntry entry = registry.create("my-track");

        assertTrue(registry.isValid(entry.getSessionId(), "my-track"));
    }

    @Test
    void isValid_returnsFalseForUnknownSession() {
        assertFalse(registry.isValid("unknown-session-id", "any-track"));
    }

    @Test
    void isValid_returnsFalseForWrongTrackName() throws Exception {
        createTrackZip("my-track");
        JukeSessionEntry entry = registry.create("my-track");

        assertFalse(registry.isValid(entry.getSessionId(), "wrong-track"));
    }

    @Test
    void invalidate_makesSubsequentIsValidReturnFalse() throws Exception {
        createTrackZip("my-track");
        JukeSessionEntry entry = registry.create("my-track");

        assertTrue(registry.isValid(entry.getSessionId(), "my-track"));

        registry.invalidate(entry.getSessionId());

        assertFalse(registry.isValid(entry.getSessionId(), "my-track"));
    }

    @Test
    void get_returnsEntryForKnownSession() throws Exception {
        createTrackZip("my-track");
        JukeSessionEntry entry = registry.create("my-track");

        Optional<JukeSessionEntry> result = registry.get(entry.getSessionId());

        assertTrue(result.isPresent());
        assertEquals(entry.getSessionId(), result.get().getSessionId());
    }

    @Test
    void get_returnsEmptyForUnknownSession() {
        Optional<JukeSessionEntry> result = registry.get("nonexistent");

        assertFalse(result.isPresent());
    }

    @Test
    void multipleSessions_areIndependent() throws Exception {
        createTrackZip("track-a");
        createTrackZip("track-b");

        JukeSessionEntry entryA = registry.create("track-a");
        JukeSessionEntry entryB = registry.create("track-b");

        assertNotEquals(entryA.getSessionId(), entryB.getSessionId());
        assertEquals(2, registry.size());

        assertTrue(registry.isValid(entryA.getSessionId(), "track-a"));
        assertTrue(registry.isValid(entryB.getSessionId(), "track-b"));
        assertFalse(registry.isValid(entryA.getSessionId(), "track-b"));

        registry.invalidate(entryA.getSessionId());
        assertEquals(1, registry.size());
        assertFalse(registry.isValid(entryA.getSessionId(), "track-a"));
        assertTrue(registry.isValid(entryB.getSessionId(), "track-b"));
    }

    @Test
    void evictExpired_noSessions_doesNotThrow() {
        assertDoesNotThrow(() -> registry.evictExpired());
    }

    @Test
    void snapshot_returnsViewOfCurrentSessions() throws Exception {
        assertEquals(0, registry.size());
        assertTrue(registry.snapshot().isEmpty());

        createTrackZip("snap-track");
        registry.create("snap-track");
        assertEquals(1, registry.size());
        assertEquals(1, registry.snapshot().size());
    }

    @Test
    void invalidate_nonExistentSession_noException() {
        assertDoesNotThrow(() -> registry.invalidate("non-existent-session-id"));
    }
}
