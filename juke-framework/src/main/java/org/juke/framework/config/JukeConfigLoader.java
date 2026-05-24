package org.juke.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads Juke configuration from YAML files on the classpath and merges
 * them in profile order.
 * <p>
 * <b>Resolution order</b> (later values override earlier ones):
 * <ol>
 *     <li>{@code juke-defaults.yml} — ships inside juke-framework.jar with
 *         baseline defaults</li>
 *     <li>{@code application.yml} — from the consuming application's classpath</li>
 *     <li>{@code application-{profile}.yml} — per-profile overrides from the
 *         consuming application (e.g. {@code application-local.yml},
 *         {@code application-record.yml})</li>
 *     <li>JVM system properties ({@code -Djuke.path=...}) — backward-compatible
 *         escape hatch</li>
 * </ol>
 *
 * <h3>Profiles</h3>
 * Active profiles are resolved from (in order):
 * <ol>
 *     <li>{@code spring.profiles.active} system property</li>
 *     <li>{@code SPRING_PROFILES_ACTIVE} environment variable</li>
 *     <li>{@code juke.profiles.active} system property (Juke-specific fallback)</li>
 *     <li>{@code JUKE_PROFILES_ACTIVE} environment variable</li>
 * </ol>
 *
 * <h3>Intended profile names</h3>
 * <ul>
 *     <li><b>Environment</b>: {@code local}, {@code uat}, {@code prod}</li>
 *     <li><b>Mode</b>: {@code record}, {@code replay}</li>
 * </ul>
 * These are combined with comma-separation:
 * {@code -Dspring.profiles.active=local,record}
 *
 * @see JukeConfig
 */
public class JukeConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(JukeConfigLoader.class);

    private static volatile JukeConfig cachedConfig;

    private JukeConfigLoader() {
    }

    /**
     * Returns the resolved {@link JukeConfig}, loading and merging YAML files
     * on the first call and caching the result.
     */
    public static JukeConfig getConfig() {
        if (cachedConfig == null) {
            synchronized (JukeConfigLoader.class) {
                if (cachedConfig == null) {
                    cachedConfig = load();
                    LOG.info("Juke configuration loaded: {}", cachedConfig);
                }
            }
        }
        return cachedConfig;
    }

    /**
     * Forces a reload of the configuration. Useful in tests or after
     * programmatic changes to system properties.
     */
    public static void reload() {
        synchronized (JukeConfigLoader.class) {
            cachedConfig = null;
        }
    }

    /**
     * Allows tests to inject a specific config without YAML loading.
     */
    public static void setConfig(JukeConfig config) {
        synchronized (JukeConfigLoader.class) {
            cachedConfig = config;
        }
    }

    // ------------------------------------------------------------------ Load

    private static JukeConfig load() {
        Map<String, Object> merged = new LinkedHashMap<>();
        loadDefaults(merged);
        loadApplicationYml(merged);
        loadProfiles(merged);

        JukeConfig config = fromMap(merged);
        applySystemPropertyOverrides(config);
        return config;
    }

    /** Merges {@code juke-defaults.yml} shipped inside {@code juke-framework.jar}. */
    private static void loadDefaults(Map<String, Object> merged) {
        merge(merged, loadYaml("juke-defaults.yml"));
    }

    /** Merges {@code application.yml} from the consuming app's classpath. */
    private static void loadApplicationYml(Map<String, Object> merged) {
        merge(merged, loadYaml("application.yml"));
    }

    /**
     * Merges per-profile overrides from the consuming app's classpath —
     * e.g. {@code application-local.yml}, {@code application-record.yml} — in
     * the order profiles were declared, so later profiles win.
     */
    private static void loadProfiles(Map<String, Object> merged) {
        List<String> profiles = resolveActiveProfiles();
        LOG.debug("Active profiles: {}", profiles);
        for (String profile : profiles) {
            merge(merged, loadYaml("application-" + profile.trim() + ".yml"));
        }
    }

    // -------------------------------------------------------- Profile resolution

    static List<String> resolveActiveProfiles() {
        String profiles = System.getProperty("spring.profiles.active");
        if (isBlank(profiles)) {
            profiles = System.getenv("SPRING_PROFILES_ACTIVE");
        }
        if (isBlank(profiles)) {
            profiles = System.getProperty("juke.profiles.active");
        }
        if (isBlank(profiles)) {
            profiles = System.getenv("JUKE_PROFILES_ACTIVE");
        }
        if (isBlank(profiles)) {
            return new ArrayList<>();
        }
        return Arrays.asList(profiles.split(","));
    }

    // -------------------------------------------------------- YAML parsing

    private static Map<String, Object> loadYaml(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourceName)) {
            if (stream == null) {
                LOG.debug("YAML resource not found on classpath: {}", resourceName);
                return new LinkedHashMap<>();
            }
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(stream);
            if (loaded instanceof Map) {
                // SnakeYAML returns a raw Map; the root of a Juke YAML doc is
                // conventionally keyed by String.
                @SuppressWarnings("unchecked")
                Map<String, Object> root = (Map<String, Object>) loaded;
                return root;
            }
            return new LinkedHashMap<>();
        } catch (Exception e) {
            LOG.warn("Failed to load YAML {}: {}", resourceName, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    // -------------------------------------------------------- Map merging

    /**
     * Deep-merges {@code overlay} into {@code base} — nested maps are merged
     * recursively, scalar values in overlay override base.
     */
    private static void merge(Map<String, Object> base, Map<String, Object> overlay) {
        for (Map.Entry<String, Object> entry : overlay.entrySet()) {
            String key = entry.getKey();
            Object overlayVal = entry.getValue();
            Object baseVal = base.get(key);

            if (baseVal instanceof Map && overlayVal instanceof Map) {
                // Both sides guarded by instanceof above; YAML-sourced nested
                // maps conventionally use String keys.
                @SuppressWarnings("unchecked")
                Map<String, Object> baseMap = (Map<String, Object>) baseVal;
                @SuppressWarnings("unchecked")
                Map<String, Object> overlayMap = (Map<String, Object>) overlayVal;
                merge(baseMap, overlayMap);
            } else {
                base.put(key, overlayVal);
            }
        }
    }

    // ---------------------------------------------------- Map → JukeConfig

    private static JukeConfig fromMap(Map<String, Object> root) {
        JukeConfig config = new JukeConfig();

        Object jukeNode = root.get("juke");
        if (!(jukeNode instanceof Map)) {
            return config;
        }

        // Guarded by instanceof above; the "juke" node is expected to be a
        // String-keyed map per the config schema.
        @SuppressWarnings("unchecked")
        Map<String, Object> juke = (Map<String, Object>) jukeNode;

        if (juke.containsKey("mode")) {
            config.setMode(String.valueOf(juke.get("mode")));
        }
        if (juke.containsKey("path")) {
            config.setPath(resolvePlaceholders(String.valueOf(juke.get("path"))));
        }
        if (juke.containsKey("zip")) {
            config.setZip(String.valueOf(juke.get("zip")));
        }
        if (juke.containsKey("disabled")) {
            Object disabled = juke.get("disabled");
            if (disabled instanceof Boolean) {
                config.setDisabled((Boolean) disabled);
            } else {
                config.setDisabled(Boolean.parseBoolean(String.valueOf(disabled)));
            }
        }

        return config;
    }

    // ---------------------------------------- System property backward compat

    /**
     * If legacy VM arguments are present, they override the YAML values.
     * This provides a seamless migration path — existing {@code -Djuke.path}
     * flags continue to work.
     */
    private static void applySystemPropertyOverrides(JukeConfig config) {
        String sysPropMode = System.getProperty("juke");
        if (!isBlank(sysPropMode)) {
            LOG.debug("Overriding juke.mode from system property: {}", sysPropMode);
            config.setMode(sysPropMode);
        }

        String sysPropPath = System.getProperty("juke.path");
        if (!isBlank(sysPropPath)) {
            LOG.debug("Overriding juke.path from system property: {}", sysPropPath);
            config.setPath(sysPropPath);
        }

        String sysPropZip = System.getProperty("juke.zip");
        if (!isBlank(sysPropZip)) {
            LOG.debug("Overriding juke.zip from system property: {}", sysPropZip);
            config.setZip(sysPropZip);
        }
    }

    // ----------------------------------------------------------------- Util

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /**
     * Resolves {@code ${...}} placeholders in a string using system properties
     * first, then environment variables. For example, {@code ${user.home}/juke}
     * becomes {@code /home/alice/juke}.
     */
    static String resolvePlaceholders(String value) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        StringBuilder result = new StringBuilder();
        int pos = 0;
        while (pos < value.length()) {
            int start = value.indexOf("${", pos);
            if (start < 0) {
                result.append(value, pos, value.length());
                break;
            }
            result.append(value, pos, start);
            int end = value.indexOf('}', start + 2);
            if (end < 0) {
                // Unclosed placeholder — append as-is
                result.append(value, start, value.length());
                break;
            }
            String key = value.substring(start + 2, end);
            String resolved = System.getProperty(key);
            if (resolved == null) {
                resolved = System.getenv(key);
            }
            result.append(resolved != null ? resolved : value.substring(start, end + 1));
            pos = end + 1;
        }
        return result.toString();
    }
}

