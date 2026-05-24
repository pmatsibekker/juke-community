package org.juke.framework.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 2 — value-type assertions for the descriptor used by the bundle packager.
 */
class UiArtefactDescriptorTest {

    @Test
    void constructor_storesAllFields() {
        UiArtefactDescriptor desc = new UiArtefactDescriptor("a.zip", "application/zip", "Display");
        assertEquals("a.zip", desc.filename());
        assertEquals("application/zip", desc.mediaType());
        assertEquals("Display", desc.displayName());
    }

    @Test
    void zip_factoryComposesFilenameFromHarnessId() {
        UiArtefactDescriptor desc = UiArtefactDescriptor.zip("playwright", "Playwright Trace");
        assertEquals("playwright.zip", desc.filename());
        assertEquals("application/zip", desc.mediaType());
        assertEquals("Playwright Trace", desc.displayName());
    }

    @Test
    void toString_containsFilenameAndMediaType() {
        String s = new UiArtefactDescriptor("x.zip", "application/zip", "X").toString();
        assertTrue(s.contains("x.zip"));
        assertTrue(s.contains("application/zip"));
    }

    @Test
    void constructor_rejectsNullFilename() {
        assertThrows(NullPointerException.class,
                () -> new UiArtefactDescriptor(null, "application/zip", "Display"));
    }

    @Test
    void constructor_rejectsNullMediaType() {
        assertThrows(NullPointerException.class,
                () -> new UiArtefactDescriptor("a.zip", null, "Display"));
    }

    @Test
    void constructor_rejectsNullDisplayName() {
        assertThrows(NullPointerException.class,
                () -> new UiArtefactDescriptor("a.zip", "application/zip", null));
    }
}
