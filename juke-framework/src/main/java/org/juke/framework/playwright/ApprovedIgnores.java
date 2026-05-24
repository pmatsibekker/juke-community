package org.juke.framework.playwright;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads and queries the juke-approved.json configuration file.
 * <p>
 * The file structure is a simple map of endpoint keys to arrays of JSONPath strings
 * whose values should be ignored during comparison. Example:
 * <pre>
 * {
 *   "GET /api/orders": [ "$.id", "$.createdAt", "$.items[*].uuid" ],
 *   "POST /api/checkout": [ "$.transactionId", "$.timestamp" ]
 * }
 * </pre>
 * <p>
 * The wildcard {@code [*]} in a path matches any numeric array index such as
 * {@code [0]}, {@code [1]}, etc.
 */
public class ApprovedIgnores {


    private final Map<String, List<String>> ignoreMap;

    /**
     * Creates an instance with no ignores (everything will be compared).
     */
    public ApprovedIgnores() {
        this.ignoreMap = Collections.emptyMap();
    }

    /**
     * Creates an instance from an already-parsed map.
     */
    public ApprovedIgnores(Map<String, List<String>> ignoreMap) {
        this.ignoreMap = (ignoreMap != null) ? ignoreMap : Collections.emptyMap();
    }

    /**
     * Loads juke-approved.json from a file path.
     */
    public static ApprovedIgnores fromFile(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<String>> map = mapper.readValue(
                file, new TypeReference<Map<String, List<String>>>() {});
        return new ApprovedIgnores(map);
    }

    /**
     * Loads juke-approved.json from an InputStream (e.g. classpath resource).
     */
    public static ApprovedIgnores fromStream(InputStream stream) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<String>> map = mapper.readValue(
                stream, new TypeReference<Map<String, List<String>>>() {});
        return new ApprovedIgnores(map);
    }

    /**
     * Returns the raw ignore paths for a given endpoint key, or an empty list.
     */
    public List<String> getIgnorePaths(String endpointKey) {
        return ignoreMap.getOrDefault(endpointKey, Collections.emptyList());
    }

    /**
     * Checks whether a specific JSON path should be ignored for the given endpoint.
     * Supports {@code [*]} wildcards in the configured ignore paths — e.g.
     * {@code $.items[*].uuid} matches {@code $.items[0].uuid}, {@code $.items[1].uuid}, etc.
     *
     * @param endpointKey the endpoint key, e.g. "GET /api/orders"
     * @param jsonPath    the concrete path produced by the diff, e.g. "$.items[0].uuid"
     * @return true if the path should be skipped in comparison
     */
    public boolean isIgnored(String endpointKey, String jsonPath) {
        List<String> paths = ignoreMap.get(endpointKey);
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        for (String pattern : paths) {
            if (pattern.equals(jsonPath)) {
                return true;
            }
            // Convert [*] wildcard to regex [\d+] for array index matching
            if (pattern.contains("[*]")) {
                // Split on [*], quote each literal segment, rejoin with [\d+]
                String[] segments = pattern.split("\\[\\*\\]", -1);
                StringBuilder regex = new StringBuilder("^");
                for (int i = 0; i < segments.length; i++) {
                    if (i > 0) {
                        regex.append("\\[\\d+\\]");
                    }
                    regex.append(Pattern.quote(segments[i]));
                }
                regex.append("$");
                if (Pattern.matches(regex.toString(), jsonPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the full map of endpoint keys to ignore paths.
     */
    public Map<String, List<String>> getIgnoreMap() {
        return Collections.unmodifiableMap(ignoreMap);
    }
}

