package org.juke.framework.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link org.juke.framework.util.JukeArgsSidecarGenerator}.
 * Tests the public static {@code generateArgsSidecars} method and verifies
 * that {@code .args.json} sidecars are created for response entries.
 */
class JukeArgsSidecarGeneratorTest {

    @TempDir
    Path tempDir;

    /**
     * Creates a minimal ZIP containing a response entry (no .args.json sidecar yet).
     */
    private File createInputZip(String entryName, String entryContent) throws IOException {
        File zip = tempDir.resolve("input.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(entryContent.getBytes());
            zos.closeEntry();
        }
        return zip;
    }

    @Test
    void generateArgsSidecars_createsArgsJsonForResponseEntry() throws Exception {
        // Create a zip with one response entry and no juke.json
        String entryName = "IGreetingService.$greet.1.json";
        File inputZip = createInputZip(entryName, "\"hello\"");
        File outputZip = tempDir.resolve("output.zip").toFile();

        org.juke.framework.util.JukeArgsSidecarGenerator.generateArgsSidecars(inputZip, outputZip);

        assertTrue(outputZip.exists(), "Output ZIP should be created");

        Set<String> names = ZipUtil.getFileNamesFromZipFile(outputZip.getAbsolutePath());
        // Original entry is preserved
        assertTrue(names.contains(entryName),
                "Original entry should be in output. Got: " + names);
        // .args.json sidecar should be added
        assertTrue(names.contains("IGreetingService.$greet.1.args.json"),
                "args sidecar should be added. Got: " + names);
    }

    @Test
    void generateArgsSidecars_skipsExistingArgsSidecar() throws Exception {
        // ZIP already has both a response entry AND a sidecar
        File zip = tempDir.resolve("existing-args.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("Svc.$fetch.1.json"));
            zos.write("\"data\"".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Svc.$fetch.1.args.json"));
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        File outputZip = tempDir.resolve("existing-out.zip").toFile();

        org.juke.framework.util.JukeArgsSidecarGenerator.generateArgsSidecars(zip, outputZip);

        Set<String> names = ZipUtil.getFileNamesFromZipFile(outputZip.getAbsolutePath());
        // Both entries preserved, no duplicate sidecar
        assertTrue(names.contains("Svc.$fetch.1.json"));
        assertTrue(names.contains("Svc.$fetch.1.args.json"));
        // count of args entries should still be 1
        long argsCount = names.stream().filter(n -> n.endsWith(".args.json")).count();
        assertEquals(1, argsCount);
    }

    @Test
    void generateArgsSidecars_ignoresTypeAndArgsEntries() throws Exception {
        // type entries and args entries should not get their own sidecars
        File zip = tempDir.resolve("type-entries.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("Svc.$m.1.type.json"));
            zos.write("\"java.lang.String\"".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Svc.$m.1.args.json"));
            zos.write("{}".getBytes());
            zos.closeEntry();
        }
        File outputZip = tempDir.resolve("type-out.zip").toFile();

        org.juke.framework.util.JukeArgsSidecarGenerator.generateArgsSidecars(zip, outputZip);

        Set<String> names = ZipUtil.getFileNamesFromZipFile(outputZip.getAbsolutePath());
        // No new sidecar should be added for .type. or .args. entries
        long extraArgs = names.stream()
                .filter(n -> n.endsWith(".args.json") && !n.equals("Svc.$m.1.args.json"))
                .count();
        assertEquals(0, extraArgs, "No extra .args.json should be created for .type./.args. entries");
    }

    @Test
    void generateArgsSidecars_withJukeJson_parsesMetadata() throws Exception {
        // ZIP contains a juke.json that describes a method with a String param
        String jukeJson = "{"
                + "\"svc\":{\"className\":\"com.example.OrderService\","
                + "\"methods\":[{\"method\":\"bill\","
                + "\"inputParameters\":[{\"className\":\"double\"}]}]}}";

        File zip = tempDir.resolve("with-juke.zip").toFile();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("juke.json"));
            zos.write(jukeJson.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("com.example.OrderService.$bill.1.json"));
            zos.write("\"$42.00\"".getBytes());
            zos.closeEntry();
        }
        File outputZip = tempDir.resolve("juke-out.zip").toFile();

        org.juke.framework.util.JukeArgsSidecarGenerator.generateArgsSidecars(zip, outputZip);

        Set<String> names = ZipUtil.getFileNamesFromZipFile(outputZip.getAbsolutePath());
        assertTrue(names.contains("com.example.OrderService.$bill.1.args.json"),
                "args sidecar should be generated. Got: " + names);
    }
}

