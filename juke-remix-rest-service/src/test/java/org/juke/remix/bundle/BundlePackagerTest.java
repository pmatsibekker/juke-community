package org.juke.remix.bundle;

import org.juke.framework.harness.UiArtefactDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundlePackagerTest {

    private BundlePackager packager;

    @BeforeEach
    void setUp() {
        packager = new BundlePackager();
    }

    @Test
    void packsAndUnpacksJukeOnlyBundle() {
        byte[] juke = sampleZip("Hello.json", "{\"value\":42}");

        byte[] bundle = packager.packJukeOnly("login-happy-path", juke);
        UnpackedBundle unpacked = packager.unpack(bundle);

        assertThat(unpacked.isJukeOnly()).isTrue();
        assertThat(unpacked.uiZipBytes()).isEmpty();
        assertThat(unpacked.manifest().getBundleName()).isEqualTo("login-happy-path");
        assertThat(unpacked.manifest().getJuke().getFormatVersion())
                .isEqualTo(BundlePackager.CURRENT_JUKE_FORMAT_VERSION);
        assertThat(unpacked.jukeZipBytes()).isEqualTo(juke);
    }

    @Test
    void packsAndUnpacksCombinedBundle() {
        byte[] juke = sampleZip("Hello.json", "{\"value\":42}");
        byte[] ui = sampleZip("trace.json", "{\"playwright\":\"trace\"}");
        UiArtefactDescriptor descriptor = UiArtefactDescriptor.zip("playwright", "Playwright trace");

        byte[] bundle = packager.packCombined("checkout-flow", juke, ui, descriptor, "playwright");
        UnpackedBundle unpacked = packager.unpack(bundle);

        assertThat(unpacked.isJukeOnly()).isFalse();
        assertThat(unpacked.manifest().hasUiSide()).isTrue();
        assertThat(unpacked.manifest().getUiArtefact().getHarness()).isEqualTo("playwright");
        assertThat(unpacked.manifest().getUiArtefact().getFilename()).isEqualTo("playwright.zip");
        assertThat(unpacked.uiZipBytes()).isPresent();
        assertThat(unpacked.uiZipBytes().get()).isEqualTo(ui);
        assertThat(unpacked.jukeZipBytes()).isEqualTo(juke);
    }

    @Test
    void manifestRecordsAccurateChecksumsAndSizes() {
        byte[] juke = sampleZip("a.json", "{}");
        byte[] ui = sampleZip("b.json", "{}");

        byte[] bundle = packager.packCombined("n", juke, ui,
                UiArtefactDescriptor.zip("playwright", "Playwright"), "playwright");
        UnpackedBundle unpacked = packager.unpack(bundle);

        BundleManifest m = unpacked.manifest();
        assertThat(m.getJuke().getSha256()).isEqualTo(BundlePackager.sha256Hex(juke));
        assertThat(m.getJuke().getSizeBytes()).isEqualTo(juke.length);
        assertThat(m.getUiArtefact().getSha256()).isEqualTo(BundlePackager.sha256Hex(ui));
        assertThat(m.getUiArtefact().getSizeBytes()).isEqualTo(ui.length);
    }

    @Test
    void rejectsBundleMissingManifest() {
        // A bundle that has juke.zip but no manifest.json
        byte[] bundle = buildRawZip(Map.of("juke.zip", new byte[]{1, 2, 3}));

        assertThatThrownBy(() -> packager.unpack(bundle))
                .isInstanceOf(BundleFormatException.class)
                .hasMessageContaining("manifest.json");
    }

    @Test
    void rejectsBundleMissingJukeEntry() {
        // Manifest references juke.zip but the entry is absent
        String manifest = "{\"bundleName\":\"x\",\"juke\":{\"filename\":\"juke.zip\"," +
                "\"sha256\":\"\",\"sizeBytes\":0,\"formatVersion\":2}}";
        byte[] bundle = buildRawZip(Map.of("manifest.json", manifest.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> packager.unpack(bundle))
                .isInstanceOf(BundleFormatException.class)
                .hasMessageContaining("juke.zip");
    }

    @Test
    void rejectsCorruptedBytesViaChecksum() {
        byte[] juke = sampleZip("a.json", "{}");
        byte[] bundle = packager.packJukeOnly("x", juke);

        // Read entries, swap juke.zip bytes for tampered ones, re-pack with the original manifest
        Map<String, byte[]> entries = readAllEntries(bundle);
        entries.put("juke.zip", new byte[]{99, 99, 99}); // tampered content
        byte[] tampered = buildRawZip(entries);

        assertThatThrownBy(() -> packager.unpack(tampered))
                .isInstanceOf(BundleFormatException.class)
                .hasMessageContaining("Checksum mismatch");
    }

    @Test
    void rejectsNonZipPayload() {
        byte[] notAZip = "this is not a zip".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> packager.unpack(notAZip))
                .isInstanceOf(BundleFormatException.class);
    }

    @Test
    void unpackingProducesDefensiveCopiesOfByteArrays() {
        byte[] juke = sampleZip("a.json", "{}");
        byte[] bundle = packager.packJukeOnly("x", juke);

        UnpackedBundle unpacked = packager.unpack(bundle);
        byte[] firstRead = unpacked.jukeZipBytes();
        firstRead[0] = (byte) ~firstRead[0];
        byte[] secondRead = unpacked.jukeZipBytes();

        assertThat(secondRead).isEqualTo(juke);
        assertThat(secondRead).isNotEqualTo(firstRead);
    }

    // ---------------------------------------------------------------- helpers

    private static byte[] sampleZip(String entryName, String content) {
        return buildRawZip(Map.of(entryName, content.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] buildRawZip(Map<String, byte[]> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return out.toByteArray();
    }

    private static Map<String, byte[]> readAllEntries(byte[] zipBytes) {
        Map<String, byte[]> map = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = zip.read(buf)) > 0) b.write(buf, 0, n);
                map.put(e.getName(), b.toByteArray());
                zip.closeEntry();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return map;
    }
}
