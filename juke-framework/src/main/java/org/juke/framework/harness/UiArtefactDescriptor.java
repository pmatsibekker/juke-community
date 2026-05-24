package org.juke.framework.harness;

import java.util.Objects;

/**
 * Describes the artefact a {@link UiHarness} produces per recording session. Used by
 * {@code BundlePackager} to derive the inner-ZIP filename inside a combined bundle (e.g.
 * {@code playwright.zip}) and by the admin UI to render harness-appropriate download labels.
 */
public final class UiArtefactDescriptor {

    private final String filename;
    private final String mediaType;
    private final String displayName;

    public UiArtefactDescriptor(String filename, String mediaType, String displayName) {
        this.filename = Objects.requireNonNull(filename, "filename");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * Filename used inside the combined bundle ZIP, e.g. {@code "playwright.zip"}. Convention:
     * {@code <harnessId>.zip}. Must not contain path separators.
     */
    public String filename() {
        return filename;
    }

    /** MIME type, typically {@code "application/zip"}. */
    public String mediaType() {
        return mediaType;
    }

    /** Human-readable label shown in the admin UI. */
    public String displayName() {
        return displayName;
    }

    /** Convenience for the common "&lt;id&gt;.zip + application/zip" case. */
    public static UiArtefactDescriptor zip(String harnessId, String displayName) {
        return new UiArtefactDescriptor(harnessId + ".zip", "application/zip", displayName);
    }

    @Override
    public String toString() {
        return "UiArtefactDescriptor{" + filename + ", " + mediaType + "}";
    }
}
