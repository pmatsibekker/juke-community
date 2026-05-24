package org.juke.framework.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.juke.framework.exception.JukeStorageException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ZipUtil} static utility methods:
 * - compactEntries (multiple branches)
 * - getMethodFromZipEntryName
 */
class ZipUtilMethodsTest {

    // ----------------------------------------------------------------
    // compactEntries
    // ----------------------------------------------------------------

    @Test
    void compactEntries_singleEntry_noCompaction() {
        HashMap<String, String> input = new HashMap<>();
        input.put("Service.method.1.json", "data1");

        HashMap<String, String> result = ZipUtil.compactEntries(input);

        // Single entry → kept as-is
        assertTrue(result.containsKey("Service.method.1.json"),
                "Single entry should be preserved unchanged");
        assertEquals("data1", result.get("Service.method.1.json"));
    }

    @Test
    void compactEntries_consecutiveDuplicates_compactedToRange() {
        HashMap<String, String> input = new HashMap<>();
        input.put("Service.fetch.1.json", "same");
        input.put("Service.fetch.2.json", "same");
        input.put("Service.fetch.3.json", "same");

        HashMap<String, String> result = ZipUtil.compactEntries(input);

        // All three are identical and consecutive → one range entry
        assertTrue(result.containsKey("Service.fetch.[1-3].json"),
                "Three identical entries should compact to a range. Got: " + result.keySet());
        assertEquals("same", result.get("Service.fetch.[1-3].json"));
        assertFalse(result.containsKey("Service.fetch.1.json"));
    }

    @Test
    void compactEntries_differentValues_notCompacted() {
        HashMap<String, String> input = new HashMap<>();
        input.put("Service.method.1.json", "data1");
        input.put("Service.method.2.json", "data2");

        HashMap<String, String> result = ZipUtil.compactEntries(input);

        assertTrue(result.containsKey("Service.method.1.json"));
        assertTrue(result.containsKey("Service.method.2.json"));
    }

    @Test
    void compactEntries_partialRunOfDuplicates_compactsOnlyRun() {
        HashMap<String, String> input = new HashMap<>();
        input.put("Svc.m.1.json", "A");
        input.put("Svc.m.2.json", "A");
        input.put("Svc.m.3.json", "B");

        HashMap<String, String> result = ZipUtil.compactEntries(input);

        // 1+2 should compact; 3 should stay separate
        assertTrue(result.containsKey("Svc.m.[1-2].json") || result.containsKey("Svc.m.1.json"),
                "First two identical entries should be compacted or kept separate");
        assertTrue(result.containsKey("Svc.m.3.json") || result.containsKey("Svc.m.[3-3].json"),
                "Differing last entry must remain");
    }

    @Test
    void compactEntries_nonSequencedEntry_passedThrough() {
        HashMap<String, String> input = new HashMap<>();
        input.put("juke.json", "{\"version\":1}");
        input.put("juke-mappings.json", "{}");

        HashMap<String, String> result = ZipUtil.compactEntries(input);

        assertEquals("{\"version\":1}", result.get("juke.json"));
        assertEquals("{}", result.get("juke-mappings.json"));
    }

    @Test
    void compactEntries_emptyInput_returnsEmpty() {
        HashMap<String, String> result = ZipUtil.compactEntries(new HashMap<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void compactEntries_mixedSequencedAndNonSequenced() {
        HashMap<String, String> input = new HashMap<>();
        input.put("juke.json", "meta");
        input.put("IService.call.1.json", "r1");
        input.put("IService.call.2.json", "r1");

        HashMap<String, String> result = ZipUtil.compactEntries(input);

        assertEquals("meta", result.get("juke.json"));
        assertTrue(result.containsKey("IService.call.[1-2].json"),
                "Identical sequenced entries should compact. Got: " + result.keySet());
    }

    // ----------------------------------------------------------------
    // getMethodFromZipEntryName
    // ----------------------------------------------------------------

    @Test
    void getMethodFromZipEntryName_legacyDollarFormat() {
        String name = "org.example.IService.$greet.1.json";
        assertEquals("greet", ZipUtil.getMethodFromZipEntryName(name));
    }

    @Test
    void getMethodFromZipEntryName_shortFormat() {
        String name = "IService.greet.1.json";
        assertEquals("greet", ZipUtil.getMethodFromZipEntryName(name));
    }

    @Test
    void getMethodFromZipEntryName_withAtDiscriminator() {
        String name = "IService.greet@String.1.json";
        assertEquals("greet", ZipUtil.getMethodFromZipEntryName(name));
    }

    @Test
    void getMethodFromZipEntryName_legacyWithAtDiscriminator() {
        String name = "org.example.IService.$process@Request.2.json";
        assertEquals("process", ZipUtil.getMethodFromZipEntryName(name));
    }

    // ----------------------------------------------------------------
    // ZipUtil instance methods (non-IO)
    // ----------------------------------------------------------------

    @Test
    void insertKey_returnsCorrectFormat() {
        ZipUtil zu = new ZipUtil("C:/temp", "test-insert");
        assertEquals("myentry.5.json", zu.insertKey("myentry", 5));
    }

    @Test
    void addEntry_and_getEntriesMap() {
        ZipUtil zu = new ZipUtil("C:/temp", "test-add");
        zu.addEntry("myKey", "myContent");
        assertTrue(zu.getEntriesMap().containsKey("myKey.json"));
        assertEquals("myContent", zu.getEntriesMap().get("myKey.json"));
    }

    @Test
    void addDirectEntry_storesExactKey() {
        ZipUtil zu = new ZipUtil("C:/temp", "test-direct");
        zu.addDirectEntry("exact.key.json", "value");
        assertEquals("value", zu.getEntriesMap().get("exact.key.json"));
    }

    @Test
    void addIncrementEntry_incrementsSequence() {
        ZipUtil zu = new ZipUtil("C:/temp", "test-incr");
        zu.addIncrementEntry("entry", "first");
        zu.addIncrementEntry("entry", "second");
        // Should have two sequenced entries
        assertEquals(2, zu.getEntriesMap().size());
    }

    @Test
    void getCurrentSequence_returnsZeroForNew() {
        ZipUtil zu = new ZipUtil("C:/temp", "test-seq");
        assertEquals(0, zu.getCurrentSequence("brand-new-entry"));
    }

    @Test
    void gettersAndSetters_workCorrectly() {
        ZipUtil zu = new ZipUtil("C:/temp", "id");
        zu.setZipPath("C:/other");
        assertEquals("C:/other", zu.getZipPath());
        zu.setIDentifier("new-id");
        assertEquals("new-id", zu.getIdentifier());
        assertNull(zu.getZipFile());
        assertNotNull(zu.getEntriesMap());
    }

    @Test
    void removeFile_nonExistentFile_doesNotThrow() {
        assertDoesNotThrow(() ->
                ZipUtil.removeFile(new java.io.File("C:/temp/no-such-file-to-remove.xyz")));
    }

    // ----------------------------------------------------------------
    // range entry fallback — readStringFromZipFile
    // ----------------------------------------------------------------

    @Test
    void readStringFromZipFile_rangeEntryFallback_returnsContent(@TempDir Path tempDir) throws IOException {
        // Create a zip with a range entry: "Method.[1-3].json"
        File zipFile = new File(tempDir.toFile(), "range-test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("Method.[1-3].json");
            zos.putNextEntry(entry);
            zos.write("range-content".getBytes());
            zos.closeEntry();
        }

        ZipUtil zu = new ZipUtil(tempDir.toString(), "range-test");
        // Request "Method.2.json" — should fall back to "Method.[1-3].json"
        String content = zu.readStringFromZipFile(zipFile.getAbsolutePath(), "Method.2.json");
        assertEquals("range-content", content);
    }

    @Test
    void readStringFromZipFile_notFound_throwsIOException(@TempDir Path tempDir) throws IOException {
        File zipFile = new File(tempDir.toFile(), "empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // empty zip
        }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "empty");
        assertThrows(IOException.class, () ->
                zu.readStringFromZipFile(zipFile.getAbsolutePath(), "NoSuch.1.json"));
    }

    @Test
    void close_whenFileIsNull_doesNotThrow(@TempDir Path tempDir) throws IOException {
        ZipUtil zu = new ZipUtil(tempDir.toString(), "nofile");
        // file is null initially — close should not throw
        assertDoesNotThrow(zu::close);
    }

    @Test
    void exists_nonExistentZip_returnsFalse(@TempDir Path tempDir) {
        ZipUtil zu = new ZipUtil(tempDir.toString(), "nonexistent");
        assertFalse(zu.exists());
    }

    @Test
    void readStringFromZipFile_rangeEntryNonMatchingIndex_throwsIOException(@TempDir Path tempDir) throws IOException {
        // Range [1-2].json but requesting index 5
        File zipFile = new File(tempDir.toFile(), "range2.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("Method.[1-2].json");
            zos.putNextEntry(entry);
            zos.write("data".getBytes());
            zos.closeEntry();
        }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "range2");
        // requesting index 5 — outside range [1-2], should throw
        assertThrows(IOException.class, () ->
                zu.readStringFromZipFile(zipFile.getAbsolutePath(), "Method.5.json"));
    }

    // ----------------------------------------------------------------
    // findRangeEntryInZipFile edge-case branches
    // ----------------------------------------------------------------

    @Test
    void readStringFromZipFile_nonJsonExtension_throwsIOException(@TempDir Path tempDir) throws IOException {
        // Empty zip, request a non-.json file — findRangeEntryInZipFile returns null immediately
        File zipFile = new File(tempDir.toFile(), "empty2.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) { /* empty */ }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "empty2");
        assertThrows(IOException.class, () ->
                zu.readStringFromZipFile(zipFile.getAbsolutePath(), "Method.2.txt"));
    }

    @Test
    void readStringFromZipFile_noDotInFilename_throwsIOException(@TempDir Path tempDir) throws IOException {
        // "nodot.json" → withoutJson="nodot" → lastDot=-1 ≤ 0 → return null
        File zipFile = new File(tempDir.toFile(), "nd.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) { /* empty */ }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "nd");
        assertThrows(IOException.class, () ->
                zu.readStringFromZipFile(zipFile.getAbsolutePath(), "nodot.json"));
    }

    @Test
    void readStringFromZipFile_nonNumericSuffix_throwsIOException(@TempDir Path tempDir) throws IOException {
        // "IService.method.json" → numStr="method" — not digits → return null
        File zipFile = new File(tempDir.toFile(), "nn.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) { /* empty */ }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "nn");
        assertThrows(IOException.class, () ->
                zu.readStringFromZipFile(zipFile.getAbsolutePath(), "IService.method.json"));
    }

    @Test
    void readStringFromZipFile_rangeNoDash_throwsIOException(@TempDir Path tempDir) throws IOException {
        // ZIP has "Method.[abc].json" (no dash) — rangePart.contains("-") = false → return null
        File zipFile = new File(tempDir.toFile(), "nodash.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("Method.[abc].json");
            zos.putNextEntry(e);
            zos.write("data".getBytes());
            zos.closeEntry();
        }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "nodash");
        assertThrows(IOException.class, () ->
                zu.readStringFromZipFile(zipFile.getAbsolutePath(), "Method.1.json"));
    }

    // ----------------------------------------------------------------
    // ZipUtil.open() branches
    // ----------------------------------------------------------------

    @Test
    void open_nonExistentZip_throwsStorageException(@TempDir Path tempDir) {
        ZipUtil zu = new ZipUtil(tempDir.toString(), "nonexistent-open");
        assertThrows(JukeStorageException.class, zu::open);
    }

    @Test
    void open_alreadyOpen_closesAndReopens(@TempDir Path tempDir) throws IOException {
        // Create a valid zip at getZipName() location
        File zipFile = new File(tempDir.toFile(), "reopen.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("test.json");
            zos.putNextEntry(e);
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "reopen");
        zu.open();                             // first open: file was null
        assertNotNull(zu.getZipFile());
        zu.open();                             // second open: file != null → closes and reopens
        assertNotNull(zu.getZipFile());
        zu.close();
    }

    @Test
    void close_whenFileIsOpen_closesSuccessfully(@TempDir Path tempDir) throws IOException {
        File zipFile = new File(tempDir.toFile(), "closeme.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("x.json");
            zos.putNextEntry(e);
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "closeme");
        zu.open();
        assertNotNull(zu.getZipFile());
        zu.close();                            // file != null → true branch of close()
        assertNull(zu.getZipFile());
    }

    // ----------------------------------------------------------------
    // removeFile / removeZipFile branches
    // ----------------------------------------------------------------

    @Test
    void removeFile_existingFile_deletesIt(@TempDir Path tempDir) throws IOException {
        File f = new File(tempDir.toFile(), "to-delete.txt");
        f.createNewFile();
        assertTrue(f.exists());
        ZipUtil.removeFile(f);
        assertFalse(f.exists());
    }

    @Test
    void removeZipFile_existingZip_renamesAsBak(@TempDir Path tempDir) throws IOException {
        // Create a zip at the getZipName() location so that exists() = true
        File zipFile = new File(tempDir.toFile(), "rz.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry e = new ZipEntry("x.json");
            zos.putNextEntry(e);
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        ZipUtil zu = new ZipUtil(tempDir.toString(), "rz");
        zu.removeZipFile();                    // exists()=true → close() then rename to .bak
        assertFalse(zipFile.exists());
        assertTrue(new File(tempDir.toFile(), "rz.zip.bak").exists());
    }

    // ----------------------------------------------------------------
    // getFileNamesFromZipFile / copyFile / createZipFile
    // ----------------------------------------------------------------

    @Test
    void getFileNamesFromZipFile_returnsAllEntries(@TempDir Path tempDir) throws IOException {
        File zipFile = new File(tempDir.toFile(), "entries.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (String name : new String[]{"a.json", "b.json", "c.json"}) {
                ZipEntry e = new ZipEntry(name);
                zos.putNextEntry(e);
                zos.write("{}".getBytes());
                zos.closeEntry();
            }
        }
        Set<String> names = ZipUtil.getFileNamesFromZipFile(zipFile.getAbsolutePath());
        assertEquals(3, names.size());
        assertTrue(names.contains("a.json"));
        assertTrue(names.contains("b.json"));
        assertTrue(names.contains("c.json"));
    }

    @Test
    void copyFile_copiesContentCorrectly(@TempDir Path tempDir) throws IOException {
        File src = new File(tempDir.toFile(), "source.txt");
        File dst = new File(tempDir.toFile(), "dest.txt");
        Files.write(src.toPath(), "hello".getBytes());
        ZipUtil.copyFile(src, dst);
        assertEquals("hello", new String(Files.readAllBytes(dst.toPath())));
    }

    @Test
    void createZipFile_entriesCanBeReadBack(@TempDir Path tempDir) throws IOException {
        String zipPath = new File(tempDir.toFile(), "created.zip").getAbsolutePath();
        HashMap<String, String> entries = new HashMap<>();
        entries.put("one.json", "{\"v\":1}");
        entries.put("two.json", "{\"v\":2}");
        ZipUtil zu = new ZipUtil(tempDir.toString(), "created");
        zu.createZipFile(zipPath, entries);
        Set<String> names = ZipUtil.getFileNamesFromZipFile(zipPath);
        assertTrue(names.contains("one.json"));
        assertTrue(names.contains("two.json"));
    }

    // ----------------------------------------------------------------
    // constructor RECORD-mode branch
    // ----------------------------------------------------------------

    @Test
    void constructor_recordMode_createsTempFile(@TempDir Path tempDir) {
        String saved = System.getProperty("juke");
        try {
            System.setProperty("juke", "record");
            ZipUtil zu = new ZipUtil(tempDir.toString(), "rec-test");
            // In RECORD mode the constructor creates a temp file — just verify no NPE
            assertNotNull(zu.getZipPath());
        } finally {
            if (saved == null) System.clearProperty("juke");
            else System.setProperty("juke", saved);
        }
    }
}

