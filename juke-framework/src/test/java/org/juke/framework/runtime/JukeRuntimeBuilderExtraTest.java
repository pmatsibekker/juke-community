package org.juke.framework.runtime;

import org.juke.framework.storage.JukeStorage;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the remaining branches in {@link JukeRuntime.Builder#build()}:
 * <ol>
 *   <li>active mode + null storage + no trackPath → storage stays null</li>
 *   <li>active mode + null storage + trackPath only (no name) → storage stays null</li>
 *   <li>active mode + null storage + full track → DAO created automatically</li>
 *   <li>explicit storage provided → DAO not replaced</li>
 * </ol>
 */
class JukeRuntimeBuilderExtraTest {

    @Test
    void build_activeMode_noTrackPath_storageStaysNull() {
        JukeRuntime rt = JukeRuntime.builder()
                .mode(JukeMode.RECORD)
                // No track() call → trackPath and trackName are null
                .build();
        // Compound if: effectiveStorage==null && mode.isActive() && trackPath!=null → false (trackPath is null)
        assertNull(rt.storage());
        assertEquals(JukeMode.RECORD, rt.mode());
    }

    @Test
    void build_activeMode_withTrackPathOnly_storageStaysNull() {
        JukeRuntime rt = JukeRuntime.builder()
                .mode(JukeMode.RECORD)
                .track("/some/path", null)  // trackName = null
                .build();
        // trackPath!=null but trackName==null → if condition false → storage stays null
        assertNull(rt.storage());
    }

    @Test
    void build_activeMode_withFullTrack_createsStorage(@TempDir Path tempDir) {
        // All conditions true: effectiveStorage==null, mode.isActive(), trackPath!=null, trackName!=null
        JukeRuntime rt = JukeRuntime.builder()
                .mode(JukeMode.RECORD)
                .track(tempDir.toString(), "test-track")
                .build();
        // JukeZipDAOImpl is created automatically (just creates a ZipUtil, doesn't open file)
        assertNotNull(rt.storage());
    }

    @Test
    void build_withExplicitStorage_storageIsPreserved() {
        JukeStorage explicitStorage = new JukeZipDAOImpl("/tmp", "explicit-track");
        JukeRuntime rt = JukeRuntime.builder()
                .mode(JukeMode.RECORD)
                .storage(explicitStorage)
                .track("/some/path", "some-name")
                .build();
        // effectiveStorage != null → if block skipped → original storage preserved
        assertSame(explicitStorage, rt.storage());
    }

    @Test
    void build_replayMode_noTrack_storageNull() {
        JukeRuntime rt = JukeRuntime.builder()
                .mode(JukeMode.REPLAY)
                .build();
        assertNull(rt.storage());
        assertEquals(JukeMode.REPLAY, rt.mode());
    }
}

