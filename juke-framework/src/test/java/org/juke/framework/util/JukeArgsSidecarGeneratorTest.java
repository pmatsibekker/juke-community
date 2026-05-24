package org.juke.framework.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JukeArgsSidecarGenerator}.
 */
class JukeArgsSidecarGeneratorTest {

    @TempDir
    Path tempDir;

    private File createMinimalZip(String name, boolean includeJukeJson) throws Exception {
        File zipFile = new File(tempDir.toFile(), name);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            if (includeJukeJson) {
                ZipEntry entry = new ZipEntry("juke.json");
                zos.putNextEntry(entry);
                zos.write("{\"ISampleService\":{\"className\":\"org.example.ISampleService\",\"methods\":[]}}".getBytes());
                zos.closeEntry();
            }
            // Add a response entry
            ZipEntry resp = new ZipEntry("ISampleService.getData.1.json");
            zos.putNextEntry(resp);
            zos.write("\"value\"".getBytes());
            zos.closeEntry();
        }
        return zipFile;
    }

    @Test
    void generateArgsSidecars_withJukeJson_doesNotThrow() throws Exception {
        File zip = createMinimalZip("test-with-juke.zip", true);
        File output = new File(tempDir.toFile(), "output-with-juke.zip");
        assertDoesNotThrow(() -> JukeArgsSidecarGenerator.generateArgsSidecars(zip, output));
        assertTrue(output.exists());
    }

    @Test
    void generateArgsSidecars_withoutJukeJson_doesNotThrow() throws Exception {
        File zip = createMinimalZip("test-no-juke.zip", false);
        File output = new File(tempDir.toFile(), "output-no-juke.zip");
        assertDoesNotThrow(() -> JukeArgsSidecarGenerator.generateArgsSidecars(zip, output));
        assertTrue(output.exists());
    }

    /**
     * Exercises every case in {@code placeholderForType} (the switch statement
     * with java.lang.String, int, Integer, long, Long, double, Double, float,
     * Float, boolean, Boolean, and a default/unknown type).
     */
    @Test
    void generateArgsSidecars_allPlaceholderTypes_noException() throws Exception {
        String jukeJson = "{"
                + "\"ISvc\":{\"className\":\"com.example.ISvc\","
                + "\"methods\":[{\"method\":\"getData\","
                + "\"inputParameters\":["
                + "{\"className\":\"java.lang.String\"},"
                + "{\"className\":\"int\"},"
                + "{\"className\":\"java.lang.Integer\"},"
                + "{\"className\":\"long\"},"
                + "{\"className\":\"java.lang.Long\"},"
                + "{\"className\":\"double\"},"
                + "{\"className\":\"java.lang.Double\"},"
                + "{\"className\":\"float\"},"
                + "{\"className\":\"java.lang.Float\"},"
                + "{\"className\":\"boolean\"},"
                + "{\"className\":\"java.lang.Boolean\"},"
                + "{\"className\":\"com.example.CustomType\"}"
                + "]}]}}";

        File zipFile = new File(tempDir.toFile(), "all-types.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry je = new ZipEntry("juke.json");
            zos.putNextEntry(je);
            zos.write(jukeJson.getBytes());
            zos.closeEntry();
            // Entry whose className and method match the juke.json metadata
            ZipEntry resp = new ZipEntry("com.example.ISvc.$getData.1.json");
            zos.putNextEntry(resp);
            zos.write("\"result\"".getBytes());
            zos.closeEntry();
        }
        File output = new File(tempDir.toFile(), "all-types-out.zip");
        assertDoesNotThrow(() -> JukeArgsSidecarGenerator.generateArgsSidecars(zipFile, output));
        assertTrue(output.exists());
    }

    /**
     * Entry name with fewer than 3 dot-segments → parseEntryClassAndMethod
     * returns {"unknown","unknown"}.
     */
    @Test
    void generateArgsSidecars_shortEntryName_treatedAsUnknown() throws Exception {
        File zipFile = new File(tempDir.toFile(), "short.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // "ab.1.json" → base="ab.1" → parts=["ab","1"] → length 2 < 3 → unknown
            ZipEntry resp = new ZipEntry("ab.1.json");
            zos.putNextEntry(resp);
            zos.write("\"x\"".getBytes());
            zos.closeEntry();
        }
        File output = new File(tempDir.toFile(), "short-out.zip");
        assertDoesNotThrow(() -> JukeArgsSidecarGenerator.generateArgsSidecars(zipFile, output));
    }

    /**
     * Entry name where the method segment does NOT start with '$' —
     * exercises the {@code methodPart.startsWith("$") == false} branch.
     */
    @Test
    void generateArgsSidecars_methodWithoutDollarPrefix_parsedCorrectly() throws Exception {
        File zipFile = new File(tempDir.toFile(), "nodollar.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            // methodPart = "getData" (no $) → startsWith("$") = false
            ZipEntry resp = new ZipEntry("ISvc.getData.1.json");
            zos.putNextEntry(resp);
            zos.write("\"val\"".getBytes());
            zos.closeEntry();
        }
        File output = new File(tempDir.toFile(), "nodollar-out.zip");
        assertDoesNotThrow(() -> JukeArgsSidecarGenerator.generateArgsSidecars(zipFile, output));
    }

    /**
     * When a .args.json sidecar already exists in the zip, the generator
     * should skip it (the {@code zip.getEntry(argsName) != null} else-branch).
     */
    @Test
    void generateArgsSidecars_existingArgsSidecar_isSkipped() throws Exception {
        File zipFile = new File(tempDir.toFile(), "existing-args.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry resp = new ZipEntry("ISvc.getData.1.json");
            zos.putNextEntry(resp);
            zos.write("\"val\"".getBytes());
            zos.closeEntry();
            // Pre-existing sidecar — generator must not create a duplicate
            ZipEntry args = new ZipEntry("ISvc.getData.1.args.json");
            zos.putNextEntry(args);
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        File output = new File(tempDir.toFile(), "existing-args-out.zip");
        assertDoesNotThrow(() -> JukeArgsSidecarGenerator.generateArgsSidecars(zipFile, output));
        assertTrue(output.exists());
    }
}

