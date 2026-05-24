package org.juke.framework.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JukeConfigLoader YAML loading, profile merging, system property
 * overrides, and placeholder resolution.
 */
public class JukeConfigLoaderTest {

    private String savedJuke;
    private String savedJukePath;
    private String savedJukeZip;
    private String savedProfiles;
    private String savedJukeProfiles;

    @BeforeEach
    void setUp() {
        // Save system properties so we can restore them
        savedJuke = System.getProperty("juke");
        savedJukePath = System.getProperty("juke.path");
        savedJukeZip = System.getProperty("juke.zip");
        savedProfiles = System.getProperty("spring.profiles.active");
        savedJukeProfiles = System.getProperty("juke.profiles.active");

        // Clear all juke-related properties for clean test isolation
        System.clearProperty("juke");
        System.clearProperty("juke.path");
        System.clearProperty("juke.zip");
        System.clearProperty("spring.profiles.active");
        System.clearProperty("juke.profiles.active");

        // Force reload so cached config doesn't carry over
        JukeConfigLoader.reload();
    }

    @AfterEach
    void tearDown() {
        // Restore original system properties
        restoreProp("juke", savedJuke);
        restoreProp("juke.path", savedJukePath);
        restoreProp("juke.zip", savedJukeZip);
        restoreProp("spring.profiles.active", savedProfiles);
        restoreProp("juke.profiles.active", savedJukeProfiles);
        JukeConfigLoader.reload();
    }

    private void restoreProp(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    // ============================ Base defaults ============================

    @Test
    void baseConfig_loadsDefaults() {
        JukeConfig config = JukeConfigLoader.getConfig();

        assertNotNull(config);
        // juke-defaults.yml sets mode=ignore, test application.yml overrides zip
        assertEquals("ignore", config.getMode());
        assertEquals("app-override-track", config.getZip());
        assertFalse(config.isDisabled());
    }

    // ============================ Record profile ===========================

    @Test
    void recordProfile_setsRecordMode() {
        System.setProperty("spring.profiles.active", "record");
        JukeConfigLoader.reload();

        JukeConfig config = JukeConfigLoader.getConfig();

        assertEquals("record", config.getMode());
    }

    // ============================ Replay profile ===========================

    @Test
    void replayProfile_setsReplayMode() {
        System.setProperty("spring.profiles.active", "replay");
        JukeConfigLoader.reload();

        JukeConfig config = JukeConfigLoader.getConfig();

        assertEquals("replay", config.getMode());
    }

    // ========================= Combined profiles ===========================

    @Test
    void localRecordProfiles_mergeCorrectly() {
        System.setProperty("spring.profiles.active", "local,record");
        JukeConfigLoader.reload();

        JukeConfig config = JukeConfigLoader.getConfig();

        // local profile sets zip=local-track, record profile sets mode=record
        assertEquals("record", config.getMode());
        assertEquals("local-track", config.getZip());
        // local sets path with ${user.home}/juke/local — verify it was resolved
        assertFalse(config.getPath().contains("${"),
                "Placeholders should be resolved, got: " + config.getPath());
        assertTrue(config.getPath().contains("juke"),
                "Path should contain 'juke', got: " + config.getPath());
    }

    // ========================= Prod profile ================================

    @Test
    void prodProfile_disablesJuke() {
        System.setProperty("spring.profiles.active", "prod");
        JukeConfigLoader.reload();

        JukeConfig config = JukeConfigLoader.getConfig();

        assertTrue(config.isDisabled());
        assertEquals("prod-track", config.getZip());
    }

    // ========================= System property overrides ====================

    @Test
    void systemProperty_overridesYaml() {
        System.setProperty("spring.profiles.active", "local");
        System.setProperty("juke.path", "/override/path");
        System.setProperty("juke.zip", "override-zip");
        System.setProperty("juke", "record");
        JukeConfigLoader.reload();

        JukeConfig config = JukeConfigLoader.getConfig();

        assertEquals("/override/path", config.getPath());
        assertEquals("override-zip", config.getZip());
        assertEquals("record", config.getMode());
    }

    // ========================= Juke-specific profile key ====================

    @Test
    void jukeProfilesActive_worksAsFallback() {
        System.setProperty("juke.profiles.active", "replay");
        JukeConfigLoader.reload();

        JukeConfig config = JukeConfigLoader.getConfig();

        assertEquals("replay", config.getMode());
    }

    // ========================= Placeholder resolution ======================

    @Test
    void resolvePlaceholders_expandsSystemProperties() {
        String resolved = JukeConfigLoader.resolvePlaceholders("${user.home}/juke/data");

        String userHome = System.getProperty("user.home");
        assertNotNull(userHome);
        assertEquals(userHome + "/juke/data", resolved);
    }

    @Test
    void resolvePlaceholders_leavesUnknownPlaceholders() {
        String resolved = JukeConfigLoader.resolvePlaceholders("${nonexistent.prop}/data");

        assertEquals("${nonexistent.prop}/data", resolved);
    }

    @Test
    void resolvePlaceholders_handlesNoPlaceholders() {
        assertEquals("/simple/path", JukeConfigLoader.resolvePlaceholders("/simple/path"));
        assertNull(JukeConfigLoader.resolvePlaceholders(null));
    }

    // ========================= setConfig for tests =========================

    @Test
    void setConfig_injectCustomConfig() {
        JukeConfig custom = new JukeConfig();
        custom.setMode("record");
        custom.setPath("/test/path");
        custom.setZip("test-zip");
        JukeConfigLoader.setConfig(custom);

        JukeConfig loaded = JukeConfigLoader.getConfig();
        assertSame(custom, loaded);
        assertEquals("record", loaded.getMode());
        assertEquals("/test/path", loaded.getPath());
    }

    // ========================= Layering verification =========================

    @Test
    void layering_appYmlOverridesFrameworkDefaults_profileOverridesBoth() {
        // With no profiles active, juke-defaults.yml provides mode=ignore,
        // and test application.yml overrides zip to app-override-track
        JukeConfig baseConfig = JukeConfigLoader.getConfig();
        assertEquals("ignore", baseConfig.getMode());
        assertEquals("app-override-track", baseConfig.getZip());

        // Now activate local,record profiles — profile YMLs override both
        JukeConfigLoader.reload();
        System.setProperty("spring.profiles.active", "local,record");
        JukeConfigLoader.reload();

        JukeConfig profileConfig = JukeConfigLoader.getConfig();
        // application-record.yml sets mode=record
        assertEquals("record", profileConfig.getMode());
        // application-local.yml sets zip=local-track, overriding app-override-track
        assertEquals("local-track", profileConfig.getZip());
    }

    // ========================= ConfigUtil integration ======================

    @Test
    void resolvePlaceholders_unclosedPlaceholder_appendsAsIs() {
        // Unclosed ${ — should be appended as-is
        String result = JukeConfigLoader.resolvePlaceholders("/path/${unclosed");
        assertEquals("/path/${unclosed", result);
    }

    @Test
    void resolvePlaceholders_multiplePlaceholders_resolvesBoth() {
        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name");
        String result = JukeConfigLoader.resolvePlaceholders("${user.home}/${os.name}");
        assertEquals(home + "/" + os, result);
    }

    @Test
    void configUtil_delegatesToJukeConfigLoader() {
        JukeConfig custom = new JukeConfig();
        custom.setMode("replay");
        custom.setPath("/config/util/test");
        custom.setZip("cu-zip");
        JukeConfigLoader.setConfig(custom);

        String expectedPath = org.apache.commons.io.FilenameUtils.normalize("/config/util/test");
        assertEquals(expectedPath, ConfigUtil.getJukePath());
        assertEquals("cu-zip", ConfigUtil.getJukeZip());
        assertEquals("replay", ConfigUtil.getJukeMode());
        assertFalse(ConfigUtil.isJukeDisabled());
    }

    /**
     * Exercises the {@code disabled instanceof Boolean == false} branch in
     * {@link JukeConfigLoader#fromMap}: the YAML file has {@code disabled: "true"}
     * (a quoted String), so it falls through to {@code Boolean.parseBoolean()}.
     */
    @Test
    void stringDisabledProfile_parsesDisabledCorrectly() {
        System.setProperty("spring.profiles.active", "string-disabled");
        JukeConfigLoader.reload();

        JukeConfig config = JukeConfigLoader.getConfig();
        assertTrue(config.isDisabled(),
                "disabled: \"true\" (String) should be parsed to Boolean true");
    }

    /**
     * Exercises ConfigUtil YAML-fallback branches when system properties are not
     * set but the injected JukeConfig has non-empty values:
     *   getJukePath() → configPath != null && !configPath.trim().isEmpty() → true
     *   getJukeZip()  → configZip  != null && !configZip.trim().isEmpty()  → true
     *   getJukeMode() → configMode != null && !configMode.trim().isEmpty() → true
     */
    @Test
    void configUtil_yamlFallback_usesConfigValues_whenNoPropSet() {
        // System properties already cleared by @BeforeEach
        JukeConfig cfg = new JukeConfig();
        cfg.setPath("/yaml/path");
        cfg.setZip("yaml-zip");
        cfg.setMode("record");
        JukeConfigLoader.setConfig(cfg);

        assertEquals(org.apache.commons.io.FilenameUtils.normalize("/yaml/path"),
                ConfigUtil.getJukePath());
        assertEquals("yaml-zip", ConfigUtil.getJukeZip());
        assertEquals("record", ConfigUtil.getJukeMode());
    }
}

