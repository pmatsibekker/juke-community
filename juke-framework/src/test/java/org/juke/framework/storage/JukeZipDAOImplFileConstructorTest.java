package org.juke.framework.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 — exercises the {@link JukeZipDAOImpl#JukeZipDAOImpl(File)} constructor added so
 * {@code BundleBackedSessionResolver} can hand the DAO a per-session temp ZIP file rather than
 * a path/name pair under the configured juke directory.
 */
class JukeZipDAOImplFileConstructorTest {

    @Test
    void fileConstructor_resolvesParentAndStripsZipSuffix(@TempDir Path tempDir) throws IOException {
        // Build a real on-disk zip with one entry so the DAO has something to inspect
        Path zip = tempDir.resolve("session-recording.zip");
        HashMap<String, String> contents = new HashMap<>();
        contents.put("ISample.greet.1.json", "\"hello\"");
        new ZipUtil(tempDir.toString(), "session-recording")
                .createZipFile(zip.toString(), contents);
        assertTrue(Files.exists(zip));

        JukeZipDAOImpl dao = new JukeZipDAOImpl(zip.toFile());

        assertTrue(dao.exists(), "DAO should resolve the same physical zip");
        assertNotNull(dao.getFileNames());
        assertTrue(dao.getFileNames().contains("ISample.greet.1.json"));
    }

    @Test
    void fileConstructor_handlesUppercaseZipSuffix(@TempDir Path tempDir) throws IOException {
        Path zip = tempDir.resolve("UPPER.ZIP");
        HashMap<String, String> contents = new HashMap<>();
        contents.put("entry.json", "{}");
        // ZipUtil composes path+name+".zip"; produce the file via the same util so the
        // suffix-stripping code path in the new constructor has something to find.
        new ZipUtil(tempDir.toString(), "UPPER")
                .createZipFile(zip.toString(), contents);

        JukeZipDAOImpl dao = new JukeZipDAOImpl(zip.toFile());
        assertTrue(dao.exists());
    }

    @Test
    void fileConstructor_handlesFileWithNoParent() {
        // When zipFile has no parent directory, the constructor must not NPE — it falls back
        // to ".". We use a relative file name that doesn't need to actually exist; the
        // constructor just sets fields.
        File f = new File("ephemeral-name.zip");
        JukeZipDAOImpl dao = new JukeZipDAOImpl(f);
        // exists() should return false (the file isn't there) but the call must not throw.
        assertEquals(false, dao.exists());
    }
}
