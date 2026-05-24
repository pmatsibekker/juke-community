package org.juke.framework.config;

/**
 * POJO that maps all {@code juke.*} YAML/YML configuration properties.
 * <p>
 * This replaces the scattered {@code -Djuke.path}, {@code -Djuke.zip}, and
 * {@code -Djuke} VM arguments with a single structured configuration that
 * can be layered per-environment (local, uat, prod) and per-mode (record, replay).
 *
 * <h3>Example YAML</h3>
 * <pre>
 * juke:
 *   mode: replay          # record | replay | ignore | disable
 *   path: /data/juke      # directory where ZIP recordings are stored
 *   zip: track            # ZIP file name (without .zip extension)
 *   disabled: false       # master kill-switch
 * </pre>
 */
public class JukeConfig {

    /** Juke mode: "record", "replay", "ignore", "disable", or "" (none). */
    private String mode;

    /** Directory where Juke ZIP recordings are stored. */
    private String path;

    /** ZIP file name (without the .zip extension). */
    private String zip;

    /** Master kill-switch — when true, Juke is completely disabled. */
    private boolean disabled;

    public JukeConfig() {
        // Defaults
        this.mode = "ignore";
        this.path = "";
        this.zip = "track";
        this.disabled = false;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @Override
    public String toString() {
        return "JukeConfig{mode='" + mode + "', path='" + path +
                "', zip='" + zip + "', disabled=" + disabled + '}';
    }
}

