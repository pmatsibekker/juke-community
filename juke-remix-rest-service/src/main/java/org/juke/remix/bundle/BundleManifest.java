package org.juke.remix.bundle;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;

/**
 * Top-level manifest serialised as {@code manifest.json} inside a bundle ZIP. See §14.1 of the
 * Scenario Testing Plan.
 *
 * <p>Two valid shapes:
 * <ul>
 *   <li>Combined bundle — both {@link #juke} and {@link #uiArtefact} populated.</li>
 *   <li>Plain Juke ZIP — {@link #juke} populated, {@link #uiArtefact} {@code null} or absent.</li>
 * </ul>
 *
 * <p>Jackson is configured to omit {@code null}s on serialisation so plain bundles do not write
 * a {@code "uiArtefact": null} entry; on read both forms are accepted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class BundleManifest {

    private String bundleName;
    private Instant createdAt;
    private Side juke;
    private UiSide uiArtefact;

    public BundleManifest() { /* Jackson */ }

    public BundleManifest(String bundleName, Instant createdAt, Side juke, UiSide uiArtefact) {
        this.bundleName = bundleName;
        this.createdAt = createdAt;
        this.juke = juke;
        this.uiArtefact = uiArtefact;
    }

    public String getBundleName() { return bundleName; }
    public void setBundleName(String bundleName) { this.bundleName = bundleName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Side getJuke() { return juke; }
    public void setJuke(Side juke) { this.juke = juke; }

    public UiSide getUiArtefact() { return uiArtefact; }
    public void setUiArtefact(UiSide uiArtefact) { this.uiArtefact = uiArtefact; }

    public boolean hasUiSide() { return uiArtefact != null; }

    /** Manifest entry for the Juke side. */
    public static class Side {
        private String filename;
        private String sha256;
        private long sizeBytes;
        private int formatVersion;

        public Side() { }

        public Side(String filename, String sha256, long sizeBytes, int formatVersion) {
            this.filename = filename;
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.formatVersion = formatVersion;
        }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getSha256() { return sha256; }
        public void setSha256(String sha256) { this.sha256 = sha256; }
        public long getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

        @JsonProperty("formatVersion")
        public int getFormatVersion() { return formatVersion; }
        public void setFormatVersion(int formatVersion) { this.formatVersion = formatVersion; }
    }

    /** Manifest entry for the UI side; carries the harness id (e.g. {@code "playwright"}). */
    public static class UiSide extends Side {
        private String harness;

        public UiSide() { super(); }

        public UiSide(String filename, String harness, String sha256, long sizeBytes) {
            super(filename, sha256, sizeBytes, 1);
            this.harness = Objects.requireNonNull(harness, "harness");
        }

        public String getHarness() { return harness; }
        public void setHarness(String harness) { this.harness = harness; }
    }
}
