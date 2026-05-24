package org.juke.remix.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.juke.framework.harness.UiArtefactDescriptor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Packs Juke (and optionally UI) ZIP bytes into a bundle archive — and reverses the operation
 * on upload — per §14.1 of the Scenario Testing Plan and Phase 2 bullet 2.
 *
 * <p>Packing produces one of two shapes:
 * <ul>
 *   <li><strong>Plain Juke ZIP</strong> — when no UI artefact is supplied: archive contains
 *       {@code manifest.json} + {@code juke.zip}.</li>
 *   <li><strong>Combined bundle</strong> — when a UI artefact is supplied: archive contains
 *       {@code manifest.json} + {@code juke.zip} + {@code <harness>.zip}.</li>
 * </ul>
 *
 * <p>Unpacking reads {@code manifest.json} first to determine the shape, then extracts each
 * inner ZIP, verifying SHA-256 checksums against the manifest. The same code path handles both
 * shapes — callers do not branch on bundle form.
 *
 * <p>This component is stateless and thread-safe. The {@link ObjectMapper} field is shared
 * because Jackson's mapper is documented thread-safe after configuration.
 */
@Component
public class BundlePackager {

    /** Path inside the bundle ZIP where the manifest lives. */
    public static final String MANIFEST_ENTRY = "manifest.json";

    /** Path inside the bundle ZIP for the Juke side — fixed regardless of harness. */
    public static final String JUKE_ENTRY = "juke.zip";

    /** Format version stamped into the manifest's juke section (matches §14.2 v2). */
    public static final int CURRENT_JUKE_FORMAT_VERSION = 2;

    private final ObjectMapper jsonMapper;

    public BundlePackager() {
        this.jsonMapper = new ObjectMapper().findAndRegisterModules();
    }

    // ===================================================================== pack

    /**
     * Pack a Juke-only bundle (no UI side).
     *
     * @param bundleName logical bundle name written into the manifest
     * @param jukeZipBytes the inner Juke ZIP
     * @return the bundle ZIP bytes
     */
    public byte[] packJukeOnly(String bundleName, byte[] jukeZipBytes) {
        return pack(bundleName, jukeZipBytes, null, null);
    }

    /**
     * Pack a combined bundle (Juke + UI).
     *
     * @param bundleName logical bundle name written into the manifest
     * @param jukeZipBytes the inner Juke ZIP
     * @param uiZipBytes the UI artefact ZIP bytes
     * @param uiDescriptor describes the UI side (filename, harness id derived from filename
     *                     prefix)
     * @param harnessId the harness id (e.g. {@code "playwright"}) — written into the manifest
     * @return the bundle ZIP bytes
     */
    public byte[] packCombined(String bundleName,
                               byte[] jukeZipBytes,
                               byte[] uiZipBytes,
                               UiArtefactDescriptor uiDescriptor,
                               String harnessId) {
        Objects.requireNonNull(uiZipBytes, "uiZipBytes");
        Objects.requireNonNull(uiDescriptor, "uiDescriptor");
        Objects.requireNonNull(harnessId, "harnessId");
        return pack(bundleName, jukeZipBytes, uiZipBytes,
                new UiPackInputs(uiDescriptor.filename(), harnessId));
    }

    private byte[] pack(String bundleName,
                        byte[] jukeZipBytes,
                        byte[] uiZipBytes,
                        UiPackInputs uiInputs) {
        Objects.requireNonNull(bundleName, "bundleName");
        Objects.requireNonNull(jukeZipBytes, "jukeZipBytes");

        BundleManifest manifest = new BundleManifest();
        manifest.setBundleName(bundleName);
        manifest.setCreatedAt(Instant.now());
        manifest.setJuke(new BundleManifest.Side(
                JUKE_ENTRY,
                sha256Hex(jukeZipBytes),
                jukeZipBytes.length,
                CURRENT_JUKE_FORMAT_VERSION));
        if (uiZipBytes != null) {
            manifest.setUiArtefact(new BundleManifest.UiSide(
                    uiInputs.filename,
                    uiInputs.harnessId,
                    sha256Hex(uiZipBytes),
                    uiZipBytes.length));
        }

        byte[] manifestJson;
        try {
            manifestJson = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialise bundle manifest", e);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                jukeZipBytes.length + (uiZipBytes == null ? 0 : uiZipBytes.length) + manifestJson.length + 1024);
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeEntry(zip, MANIFEST_ENTRY, manifestJson);
            writeEntry(zip, JUKE_ENTRY, jukeZipBytes);
            if (uiZipBytes != null) {
                writeEntry(zip, uiInputs.filename, uiZipBytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to assemble bundle ZIP", e);
        }
        return out.toByteArray();
    }

    // =================================================================== unpack

    /**
     * Auto-detect which form was uploaded (combined vs plain Juke ZIP) and extract its parts,
     * verifying SHA-256 checksums where present.
     *
     * @param bundleZipBytes the uploaded bundle bytes
     * @return the extracted parts
     * @throws BundleFormatException if the ZIP is malformed, the manifest is missing or invalid,
     *                               required entries are absent, or any checksum fails
     */
    public UnpackedBundle unpack(byte[] bundleZipBytes) {
        Objects.requireNonNull(bundleZipBytes, "bundleZipBytes");

        Map<String, byte[]> entries = readAllEntries(bundleZipBytes);

        byte[] manifestBytes = entries.get(MANIFEST_ENTRY);
        if (manifestBytes == null) {
            throw new BundleFormatException(
                    "Bundle is missing " + MANIFEST_ENTRY + ". Found entries: " + entries.keySet());
        }

        BundleManifest manifest;
        try {
            manifest = jsonMapper.readValue(manifestBytes, BundleManifest.class);
        } catch (IOException e) {
            throw new BundleFormatException(MANIFEST_ENTRY + " is not valid JSON: " + e.getMessage(), e);
        }
        if (manifest.getBundleName() == null || manifest.getBundleName().isBlank()) {
            throw new BundleFormatException(MANIFEST_ENTRY + " is missing required field 'bundleName'");
        }
        if (manifest.getJuke() == null) {
            throw new BundleFormatException(MANIFEST_ENTRY + " is missing required 'juke' section");
        }

        byte[] jukeBytes = entries.get(JUKE_ENTRY);
        if (jukeBytes == null) {
            throw new BundleFormatException("Bundle manifest references " + JUKE_ENTRY +
                    " but the entry is absent. Found entries: " + entries.keySet());
        }
        verifyChecksum(JUKE_ENTRY, jukeBytes, manifest.getJuke().getSha256());
        verifySize(JUKE_ENTRY, jukeBytes, manifest.getJuke().getSizeBytes());

        byte[] uiBytes = null;
        if (manifest.hasUiSide()) {
            BundleManifest.UiSide ui = manifest.getUiArtefact();
            if (ui.getFilename() == null || ui.getFilename().isBlank()) {
                throw new BundleFormatException("uiArtefact section is missing 'filename'");
            }
            if (ui.getHarness() == null || ui.getHarness().isBlank()) {
                throw new BundleFormatException("uiArtefact section is missing 'harness'");
            }
            uiBytes = entries.get(ui.getFilename());
            if (uiBytes == null) {
                throw new BundleFormatException("Bundle manifest references UI artefact '" +
                        ui.getFilename() + "' but the entry is absent. Found entries: " +
                        entries.keySet());
            }
            verifyChecksum(ui.getFilename(), uiBytes, ui.getSha256());
            verifySize(ui.getFilename(), uiBytes, ui.getSizeBytes());
        }

        return new UnpackedBundle(manifest, jukeBytes, uiBytes);
    }

    // =================================================================== utility

    /** Compute the SHA-256 hex digest of a byte array. */
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static Map<String, byte[]> readAllEntries(byte[] bundleZipBytes) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bundleZipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zip.closeEntry();
                    continue;
                }
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = zip.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                }
                entries.put(entry.getName(), buf.toByteArray());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new BundleFormatException("Bundle is not a valid ZIP archive: " + e.getMessage(), e);
        }
        return entries;
    }

    private static void verifyChecksum(String entryName, byte[] data, String expected) {
        if (expected == null || expected.isBlank()) {
            // Manifest did not record a checksum — skip per Phase 2 bullet 4 ("verify where present")
            return;
        }
        String actual = sha256Hex(data);
        if (!actual.equalsIgnoreCase(expected)) {
            throw new BundleFormatException(
                    "Checksum mismatch for entry '" + entryName + "': manifest says " +
                            expected + " but actual is " + actual);
        }
    }

    private static void verifySize(String entryName, byte[] data, long expectedSize) {
        if (expectedSize <= 0) return; // unknown / not recorded
        if (data.length != expectedSize) {
            throw new BundleFormatException(
                    "Size mismatch for entry '" + entryName + "': manifest says " +
                            expectedSize + " bytes but actual is " + data.length);
        }
    }

    private record UiPackInputs(String filename, String harnessId) { }
}
