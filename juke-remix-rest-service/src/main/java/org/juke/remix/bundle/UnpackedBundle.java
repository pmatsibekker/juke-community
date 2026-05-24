package org.juke.remix.bundle;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@link BundlePackager#unpack(byte[])} — the manifest plus the inner ZIP bytes.
 * For plain Juke ZIPs (no UI harness was active), {@link #uiZipBytes()} is empty and the
 * manifest's {@code uiArtefact} is {@code null}.
 *
 * <p>Returned as an immutable value so the controller layer cannot accidentally mutate
 * extracted bytes between checksum verification and persistence.
 */
public final class UnpackedBundle {

    private final BundleManifest manifest;
    private final byte[] jukeZipBytes;
    private final byte[] uiZipBytes; // nullable

    UnpackedBundle(BundleManifest manifest, byte[] jukeZipBytes, byte[] uiZipBytes) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.jukeZipBytes = Objects.requireNonNull(jukeZipBytes, "jukeZipBytes");
        this.uiZipBytes = uiZipBytes;
    }

    public BundleManifest manifest() {
        return manifest;
    }

    /** Defensive copy — callers must not retain references to the internal array. */
    public byte[] jukeZipBytes() {
        return jukeZipBytes.clone();
    }

    /** Empty for plain Juke bundles. Defensive copy when present. */
    public Optional<byte[]> uiZipBytes() {
        return uiZipBytes == null ? Optional.empty() : Optional.of(uiZipBytes.clone());
    }

    public boolean isJukeOnly() {
        return uiZipBytes == null;
    }
}
