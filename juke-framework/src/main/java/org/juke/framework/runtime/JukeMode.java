package org.juke.framework.runtime;

import org.juke.framework.proxy.JukeState;

/**
 * Typed replacement for the string-based mode constants on
 * {@link org.juke.framework.proxy.JukeState}.
 * <p>
 * An enum eliminates the "stringly typed" bugs that the {@code GLOBAL_JUKE}
 * field has historically been prone to (typos, case-sensitivity mismatches,
 * null-vs-empty ambiguity). The legacy string constants remain on
 * {@code JukeState} for backwards-compatibility; new code should reference
 * {@link JukeMode} and use {@link #fromLegacyString(String)} at the boundary.
 */
public enum JukeMode {

    /** Juke records every proxied interaction to the current track ZIP. */
    RECORD(JukeState.RECORD),

    /** Juke replays recorded interactions from the current track ZIP. */
    REPLAY(JukeState.REPLAY),

    /** Juke passes every call straight through to the real service. */
    IGNORE(JukeState.IGNORE),

    /** Juke is globally disabled — takes precedence over RECORD and REPLAY. */
    DISABLE(JukeState.DISABLE),

    /** No mode configured; treat as passthrough. */
    NONE("none");

    private final String legacyString;

    JukeMode(String legacyString) {
        this.legacyString = legacyString;
    }

    /** The legacy string constant (from {@link JukeState}) that this mode maps to. */
    public String legacyString() {
        return legacyString;
    }

    /**
     * Maps a legacy {@link JukeState} string value to the typed enum.
     * Unknown values (including {@code null}) return {@link #NONE} so callers
     * can treat the absence of a mode as passthrough without a null check.
     */
    public static JukeMode fromLegacyString(String value) {
        if (value == null) {
            return NONE;
        }
        for (JukeMode mode : values()) {
            if (mode.legacyString.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return NONE;
    }

    public boolean isRecord() { return this == RECORD; }
    public boolean isReplay() { return this == REPLAY; }
    public boolean isActive() { return this == RECORD || this == REPLAY; }
}
