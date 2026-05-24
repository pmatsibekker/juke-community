package org.juke.framework.playwright;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Core comparison engine that compares a Playwright recording (the baseline)
 * against a new Playwright run (the actual), filtering out approved-ignore paths
 * loaded from {@code juke-approved.json}.
 *
 * <h3>Usage</h3>
 * <pre>
 * ApprovedIgnores ignores = ApprovedIgnores.fromFile(new File("juke-approved.json"));
 * PlaywrightRecording baseline = engine.loadRecording(new File("baseline.json"));
 * PlaywrightRecording actual   = engine.loadRecording(new File("actual.json"));
 *
 * ComparisonReport report = engine.compare(baseline, actual, ignores);
 * engine.writeReport(report, new File("comparison-report.json"));
 * </pre>
 */
public class PlaywrightComparisonEngine {

    private static final Logger LOG = LoggerFactory.getLogger(PlaywrightComparisonEngine.class);

    private final ObjectMapper objectMapper;

    public PlaywrightComparisonEngine() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ------------------------------------------------------------------ I/O

    /**
     * Deserializes a Playwright recording JSON file.
     */
    public PlaywrightRecording loadRecording(File file) throws IOException {
        return objectMapper.readValue(file, PlaywrightRecording.class);
    }

    /**
     * Deserializes a Playwright recording from a JSON string.
     */
    public PlaywrightRecording loadRecording(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, PlaywrightRecording.class);
    }

    /**
     * Writes a {@link ComparisonReport} to a JSON file.
     */
    public void writeReport(ComparisonReport report, File outputFile) throws IOException {
        objectMapper.writeValue(outputFile, report);
    }

    /**
     * Serializes a {@link ComparisonReport} to a JSON string.
     */
    public String writeReportAsString(ComparisonReport report) throws JsonProcessingException {
        return objectMapper.writeValueAsString(report);
    }

    // ------------------------------------------------------------- Compare

    /**
     * Compares every entry in the baseline recording against the corresponding
     * entry (matched by position) in the actual recording.
     * <p>
     * Entries are correlated by ordinal position. If the actual recording has
     * fewer entries than the baseline, the missing entries are reported as
     * failures. Extra entries in the actual recording are ignored.
     *
     * @param baseline the expected recording (from a known-good Playwright run)
     * @param actual   the new recording (from the current Playwright run)
     * @param ignores  the approved-ignore configuration
     * @return a report describing every endpoint comparison
     */
    public ComparisonReport compare(PlaywrightRecording baseline,
                                    PlaywrightRecording actual,
                                    ApprovedIgnores ignores) {

        ComparisonReport report = new ComparisonReport();

        // Track per-endpoint call index so repeated calls get unique indices
        Map<String, Integer> callCounters = new HashMap<>();

        int baselineSize = baseline.getEntries().size();
        int actualSize = actual.getEntries().size();

        for (int i = 0; i < baselineSize; i++) {
            PlaywrightEntry baselineEntry = baseline.getEntries().get(i);
            String endpointKey = baselineEntry.getEndpointKey();

            int callIndex = callCounters.getOrDefault(endpointKey, 0) + 1;
            callCounters.put(endpointKey, callIndex);

            EndpointResult result = new EndpointResult(endpointKey, callIndex);

            if (i >= actualSize) {
                // No corresponding actual entry
                result.addDiff(new JsonDiff("$", baselineEntry.getResponseBody(), null));
                report.addResult(result);
                continue;
            }

            PlaywrightEntry actualEntry = actual.getEntries().get(i);

            try {
                JsonNode expectedNode = parseResponseBody(baselineEntry.getResponseBody());
                JsonNode actualNode = parseResponseBody(actualEntry.getResponseBody());
                deepCompare(expectedNode, actualNode, "$", endpointKey, ignores, result);
            } catch (JsonProcessingException e) {
                LOG.warn("Failed to parse response body for {}: {}", endpointKey, e.getMessage());
                // Fall back to raw string comparison
                if (!safeEquals(baselineEntry.getResponseBody(), actualEntry.getResponseBody())) {
                    result.addDiff(new JsonDiff("$",
                            baselineEntry.getResponseBody(),
                            actualEntry.getResponseBody()));
                }
            }

            report.addResult(result);
        }

        return report;
    }

    // --------------------------------------------------------- Deep compare

    /**
     * Recursively compares two {@link JsonNode} trees, recording diffs into
     * the given {@link EndpointResult} while skipping any paths that are
     * marked as ignored in the {@link ApprovedIgnores} configuration.
     */
    private void deepCompare(JsonNode expected, JsonNode actual,
                             String currentPath, String endpointKey,
                             ApprovedIgnores ignores, EndpointResult result) {

        // Check ignore BEFORE comparing values
        if (ignores.isIgnored(endpointKey, currentPath)) {
            result.addIgnored(currentPath);
            return;
        }

        // Both null / missing → equal
        if (isNullOrMissing(expected) && isNullOrMissing(actual)) {
            return;
        }

        // One side null / missing
        if (isNullOrMissing(expected) || isNullOrMissing(actual)) {
            result.addDiff(new JsonDiff(currentPath, nodeValue(expected), nodeValue(actual)));
            return;
        }

        // Type mismatch
        if (expected.getNodeType() != actual.getNodeType()) {
            result.addDiff(new JsonDiff(currentPath, nodeValue(expected), nodeValue(actual)));
            return;
        }

        // Object node — compare each field
        if (expected.isObject()) {
            ObjectNode expectedObj = (ObjectNode) expected;
            ObjectNode actualObj = (ObjectNode) actual;

            // Fields in expected
            Iterator<String> fieldNames = expectedObj.fieldNames();
            while (fieldNames.hasNext()) {
                String field = fieldNames.next();
                String childPath = currentPath + "." + field;
                deepCompare(expectedObj.get(field), actualObj.get(field),
                        childPath, endpointKey, ignores, result);
            }
            // Fields only in actual (added fields)
            Iterator<String> actualFieldNames = actualObj.fieldNames();
            while (actualFieldNames.hasNext()) {
                String field = actualFieldNames.next();
                if (!expectedObj.has(field)) {
                    String childPath = currentPath + "." + field;
                    if (ignores.isIgnored(endpointKey, childPath)) {
                        result.addIgnored(childPath);
                    } else {
                        result.addDiff(new JsonDiff(childPath, null, nodeValue(actualObj.get(field))));
                    }
                }
            }
            return;
        }

        // Array node — compare element by element
        if (expected.isArray()) {
            ArrayNode expectedArr = (ArrayNode) expected;
            ArrayNode actualArr = (ArrayNode) actual;

            int maxLen = Math.max(expectedArr.size(), actualArr.size());
            for (int i = 0; i < maxLen; i++) {
                String elementPath = currentPath + "[" + i + "]";
                JsonNode expectedElem = (i < expectedArr.size()) ? expectedArr.get(i) : null;
                JsonNode actualElem = (i < actualArr.size()) ? actualArr.get(i) : null;
                deepCompare(expectedElem, actualElem, elementPath, endpointKey, ignores, result);
            }
            return;
        }

        // Value node — direct comparison
        if (!expected.equals(actual)) {
            result.addDiff(new JsonDiff(currentPath, nodeValue(expected), nodeValue(actual)));
        }
    }

    // ------------------------------------------------------------ Helpers

    private JsonNode parseResponseBody(String body) throws JsonProcessingException {
        if (body == null || body.trim().isEmpty()) {
            return objectMapper.nullNode();
        }
        return objectMapper.readTree(body);
    }

    private boolean isNullOrMissing(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    /**
     * Extracts a simple Java value from a JsonNode for inclusion in diff reports.
     */
    private Object nodeValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        // For objects/arrays, return the string representation
        return node.toString();
    }

    private boolean safeEquals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}

