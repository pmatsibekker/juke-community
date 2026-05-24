package org.juke.framework.proxy;

import org.juke.framework.runtime.JukeMode;
import org.juke.framework.runtime.JukeRuntimeHolder;

/**
 * String-constant catalogue for the Juke modes plus the historic setter/getter
 * API for the "global mode" value.
 * <p>
 * Phase 3 Step D: the {@code GLOBAL_JUKE} / {@code GLOBAL_DISABLE} static
 * fields have been removed. The setter/getter methods on this class are now
 * thin adapters over {@link JukeRuntimeHolder}, which is the single source of
 * truth for the current runtime. Callers that still import this class get the
 * same behaviour; direct field access is gone.
 */
public class JukeState {


	public static final String JUKE= "juke";
	public static final String RECORD = "record";
	public static final String JUKEZIP ="juke.zip";
	public static final String REPLAY = "replay";
	public static final String IGNORE = "ignore";
	public static final String NONE = "";
	public static final String DISABLE = "disable";

	public static String getGlobaljuke() {
		// Preserve historic semantics: when nothing is configured, return null
		// rather than the empty string used internally by JukeMode.NONE.
		JukeMode mode = JukeRuntimeHolder.current().mode();
		return mode == JukeMode.NONE ? null : mode.legacyString();
	}

	public static void setGlobaljuke(String globaljuke) {
		JukeMode newMode = JukeMode.fromLegacyString(globaljuke);
		JukeRuntimeHolder.update(r -> r.withMode(newMode));
	}

	public static boolean isGlobalisable() {
		return JukeRuntimeHolder.current().mode() == JukeMode.DISABLE;
	}

	public static void setGlobalDisable(boolean globalDisable) {
		// Only downgrade to DISABLE when asked to disable; leaving it unchanged
		// when re-enabling preserves whatever mode the caller had active.
		if (globalDisable) {
			JukeRuntimeHolder.update(r -> r.withMode(JukeMode.DISABLE));
		} else {
			JukeRuntimeHolder.update(r ->
					r.mode() == JukeMode.DISABLE ? r.withMode(JukeMode.NONE) : r);
		}
	}

}
