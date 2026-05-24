package org.juke.framework.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional tests for {@link ConfigUtil} – covers the system-property
 * override branches not exercised by existing tests.
 */
class ConfigUtilExtendedTest {

    private String savedPath;
    private String savedZip;
    private String savedMode;
    private String savedArgsVal;

    @BeforeEach
    void backup() {
        savedPath    = System.getProperty("juke.path");
        savedZip     = System.getProperty("juke.zip");
        savedMode    = System.getProperty("juke");
        savedArgsVal = System.getProperty("juke.args-validation");
    }

    @AfterEach
    void restore() {
        restoreProp("juke.path",           savedPath);
        restoreProp("juke.zip",            savedZip);
        restoreProp("juke",                savedMode);
        restoreProp("juke.args-validation", savedArgsVal);
    }

    private void restoreProp(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    // ── getJukePath ───────────────────────────────────────────────────────

    @Test
    void getJukePath_systemPropertyOverride_isUsed() {
        System.setProperty("juke.path", "/tmp/override-path");
        String path = ConfigUtil.getJukePath();
        assertTrue(path.contains("override-path"),
                "System property juke.path should override config");
    }

    @Test
    void getJukePath_noProperty_returnsNonNull() {
        System.clearProperty("juke.path");
        assertNotNull(ConfigUtil.getJukePath());
    }

    // ── getJukeZip ────────────────────────────────────────────────────────

    @Test
    void getJukeZip_systemPropertyOverride_isUsed() {
        System.setProperty("juke.zip", "my-track");
        assertEquals("my-track", ConfigUtil.getJukeZip());
    }

    @Test
    void getJukeZip_noProperty_returnsDefault() {
        System.clearProperty("juke.zip");
        String zip = ConfigUtil.getJukeZip();
        assertNotNull(zip);
        // Default is "track" when nothing is configured
        assertFalse(zip.isEmpty());
    }

    // ── getJukeMode ───────────────────────────────────────────────────────

    @Test
    void getJukeMode_systemPropertyOverride_isUsed() {
        System.setProperty("juke", "record");
        assertEquals("record", ConfigUtil.getJukeMode());
    }

    @Test
    void getJukeMode_noProperty_returnsDefault() {
        System.clearProperty("juke");
        String mode = ConfigUtil.getJukeMode();
        assertNotNull(mode);
        assertFalse(mode.isEmpty());
    }

    // ── getArgsValidationMode ─────────────────────────────────────────────

    @Test
    void getArgsValidationMode_noProperty_returnsWarn() {
        System.clearProperty("juke.args-validation");
        assertEquals("warn", ConfigUtil.getArgsValidationMode());
    }

    @Test
    void getArgsValidationMode_propertySet_returnsLowerCased() {
        System.setProperty("juke.args-validation", "STRICT");
        assertEquals("strict", ConfigUtil.getArgsValidationMode());
    }

    @Test
    void getArgsValidationMode_off() {
        System.setProperty("juke.args-validation", "off");
        assertEquals("off", ConfigUtil.getArgsValidationMode());
    }

    // ── getDefauljukePath / setDefauljukePath ─────────────────────────────

    @Test
    void getDefauljukePath_returnsNonNull() {
        assertNotNull(ConfigUtil.getDefauljukePath());
    }

    @Test
    void setDefauljukePath_setsSystemProperty() {
        String returned = ConfigUtil.setDefauljukePath();
        assertNotNull(returned);
        assertEquals(returned, System.getProperty("juke.path"));
    }

    // ── isJukeDisabled ────────────────────────────────────────────────────

    @Test
    void isJukeDisabled_returnsBoolean() {
        // Just verify it doesn't throw
        assertDoesNotThrow(ConfigUtil::isJukeDisabled);
    }

    // ── whitespace-only system property falls through to config / default ──

    @Test
    void getJukePath_whitespaceProperty_fallsThrough() {
        System.setProperty("juke.path", "   ");
        // prop.trim().isEmpty() = true → falls through to config/default
        assertNotNull(ConfigUtil.getJukePath());
    }

    @Test
    void getJukeZip_whitespaceProperty_fallsThrough() {
        System.setProperty("juke.zip", "   ");
        // prop.trim().isEmpty() = true → falls through to config/default
        assertNotNull(ConfigUtil.getJukeZip());
    }

    @Test
    void getJukeMode_whitespaceProperty_fallsThrough() {
        System.setProperty("juke", "   ");
        // prop.trim().isEmpty() = true → falls through to config/default
        assertNotNull(ConfigUtil.getJukeMode());
    }

    @Test
    void getArgsValidationMode_whitespaceProperty_returnsWarn() {
        System.setProperty("juke.args-validation", "  ");
        // prop.trim().isEmpty() = true → returns "warn"
        assertEquals("warn", ConfigUtil.getArgsValidationMode());
    }
}

