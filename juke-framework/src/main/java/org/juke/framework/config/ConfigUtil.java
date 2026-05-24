package org.juke.framework.config;

import org.apache.commons.io.FilenameUtils;

import java.io.File;

public class ConfigUtil {

	private static final String TMP = FilenameUtils.normalize(System.getProperty("java.io.tmpdir") + "/juke");
	static {
		new File(TMP).mkdirs();
	}
	private ConfigUtil() {
		
	}

	/**
	 * Returns the Juke recording directory. Resolution order:
	 * <ol>
	 *     <li>{@code -Djuke.path} system property (highest priority for backward compat)</li>
	 *     <li>YAML config ({@code juke.path} from active profiles)</li>
	 *     <li>Default temp directory ({@code java.io.tmpdir/juke})</li>
	 * </ol>
	 */
	public static String getJukePath() {
		// System properties take highest priority (backward compat + test overrides)
		String prop = System.getProperty("juke.path");
		if (prop != null && !prop.trim().isEmpty()) {
			return FilenameUtils.normalize(prop);
		}
		String configPath = JukeConfigLoader.getConfig().getPath();
		if (configPath != null && !configPath.trim().isEmpty()) {
			return FilenameUtils.normalize(configPath);
		}
		return getDefauljukePath();
	}

	/**
	 * Returns the Juke ZIP file name (without .zip extension). Resolution order:
	 * <ol>
	 *     <li>{@code -Djuke.zip} system property (highest priority for backward compat)</li>
	 *     <li>YAML config ({@code juke.zip} from active profiles)</li>
	 *     <li>Default: "track"</li>
	 * </ol>
	 */
	public static String getJukeZip() {
		String prop = System.getProperty("juke.zip");
		if (prop != null && !prop.trim().isEmpty()) {
			return prop;
		}
		String configZip = JukeConfigLoader.getConfig().getZip();
		if (configZip != null && !configZip.trim().isEmpty()) {
			return configZip;
		}
		return "track";
	}

	/**
	 * Returns the Juke mode (record/replay/ignore/disable). Resolution order:
	 * <ol>
	 *     <li>{@code -Djuke} system property (highest priority for backward compat)</li>
	 *     <li>YAML config ({@code juke.mode} from active profiles)</li>
	 *     <li>Default: "ignore"</li>
	 * </ol>
	 */
	public static String getJukeMode() {
		String prop = System.getProperty("juke");
		if (prop != null && !prop.trim().isEmpty()) {
			return prop;
		}
		String configMode = JukeConfigLoader.getConfig().getMode();
		if (configMode != null && !configMode.trim().isEmpty()) {
			return configMode;
		}
		return "ignore";
	}

	/**
	 * Returns whether Juke is globally disabled.
	 */
	public static boolean isJukeDisabled() {
		return JukeConfigLoader.getConfig().isDisabled();
	}

	public static String setDefauljukePath() {
		System.setProperty("juke.path", TMP);
		return TMP;
		
	}
	public static String getDefauljukePath() {
		return TMP;
		
	}

	/**
	 * Returns the input-args validation mode for replay. Resolution order:
	 * <ol>
	 *     <li>{@code -Djuke.args-validation} system property</li>
	 *     <li>Default: "warn"</li>
	 * </ol>
	 *
	 * @return "warn", "strict", or "off"
	 */
	public static String getArgsValidationMode() {
		String prop = System.getProperty("juke.args-validation");
		if (prop != null && !prop.trim().isEmpty()) {
			return prop.trim().toLowerCase();
		}
		return "warn";
	}
}
