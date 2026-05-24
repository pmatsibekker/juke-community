package org.juke.framework.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.juke.framework.annotation.JukeIgnorable.IgnoreStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Pure-function diff engine for the input-validation hook (Plan §5.2).
 *
 * <p>Walks the recorded JSON tree, comparing every leaf against the live tree
 * and skipping paths matched by a {@link FieldIgnoreRule}. The engine returns
 * an {@link InputValidationResult} whose {@code diffs} list is empty on a
 * clean match.
 *
 * <h3>Strategy semantics</h3>
 * <ul>
 *   <li>{@link IgnoreStrategy#ALWAYS} — leaf is fully skipped, regardless of value.</li>
 *   <li>{@link IgnoreStrategy#NOT_NULL} — leaf is skipped only when both sides
 *       are non-null; null vs non-null still surfaces as a diff.</li>
 *   <li>No rule — straight value comparison.</li>
 * </ul>
 *
 * <p>The class is stateless and thread-safe.
 */
public final class InputDiffEngine {

    /**
     * Compare the recorded tree against the live tree under the given ignore rules.
     *
     * @param className   FQCN being replayed (informational; surfaces in the result)
     * @param methodName  method being replayed (informational)
     * @param step        recording step number (informational)
     * @param recorded    JSON tree from the {@code .args.json} sidecar
     * @param live        JSON tree from the live invocation
     * @param ignoreRules per-path ignore rules; may be null/empty
     * @return validation result; {@code !hasDiffs()} means the inputs matched
     */
    public InputValidationResult diff(String className,
                                      String methodName,
                                      int step,
                                      JsonNode recorded,
                                      JsonNode live,
                                      List<FieldIgnoreRule> ignoreRules) {

        Map<String, IgnoreStrategy> ruleMap = indexRules(ignoreRules);
        List<FieldDiff> diffs = new ArrayList<>();

        compare(recorded, live, "$", ruleMap, diffs);

        return new InputValidationResult(className, methodName, step, diffs);
    }

    // -------------------------------------------------------------------- recursion

    private void compare(JsonNode recorded, JsonNode live, String path,
                         Map<String, IgnoreStrategy> rules, List<FieldDiff> out) {

        IgnoreStrategy strategy = rules.get(path);

        // ALWAYS — short-circuit irrespective of values
        if (strategy == IgnoreStrategy.ALWAYS) {
            return;
        }

        boolean recMissing = isMissingOrNull(recorded);
        boolean liveMissing = isMissingOrNull(live);

        // NOT_NULL — only skip if both sides are non-null; otherwise fall through
        if (strategy == IgnoreStrategy.NOT_NULL && !recMissing && !liveMissing) {
            return;
        }

        if (recMissing && liveMissing) return;

        // Null/missing on exactly one side — diff
        if (recMissing || liveMissing) {
            out.add(new FieldDiff(path, asString(recorded), asString(live)));
            return;
        }

        // Type mismatch — record and stop recursing
        if (recorded.getNodeType() != live.getNodeType()) {
            out.add(new FieldDiff(path, asString(recorded), asString(live)));
            return;
        }

        if (recorded.isObject()) {
            // Recorded fields drive the walk
            Iterator<String> rec = recorded.fieldNames();
            while (rec.hasNext()) {
                String f = rec.next();
                compare(recorded.get(f), live.get(f), path + "." + f, rules, out);
            }
            // Extra fields on the live side: not a recorded value, so we don't
            // diff them (they're considered additive and don't break replay).
            return;
        }

        if (recorded.isArray()) {
            int max = Math.max(recorded.size(), live.size());
            for (int i = 0; i < max; i++) {
                JsonNode r = i < recorded.size() ? recorded.get(i) : null;
                JsonNode l = i < live.size() ? live.get(i) : null;
                compare(r, l, path + "[" + i + "]", rules, out);
            }
            return;
        }

        // Leaf
        if (!recorded.equals(live)) {
            out.add(new FieldDiff(path, asString(recorded), asString(live)));
        }
    }

    // -------------------------------------------------------------------- helpers

    private static Map<String, IgnoreStrategy> indexRules(List<FieldIgnoreRule> rules) {
        Map<String, IgnoreStrategy> map = new HashMap<>();
        if (rules == null) return map;
        for (FieldIgnoreRule r : rules) {
            if (r == null) continue;
            // Last-write-wins: explicit-rules in the supplied list override earlier entries.
            // The scenario service is expected to merge MANUAL rules over ANNOTATION rules
            // before passing the list in.
            map.put(r.jsonPath(), r.strategy());
        }
        return map;
    }

    private static boolean isMissingOrNull(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private static String asString(JsonNode node) {
        if (isMissingOrNull(node)) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber())  return node.numberValue().toString();
        if (node.isBoolean()) return Boolean.toString(node.asBoolean());
        return node.toString();
    }
}
