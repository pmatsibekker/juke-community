package org.juke.coverage;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimum acceptable coverage percentages (0–100) that must be met for a
 * coverage run to be considered passing.
 *
 * <p>A value of {@code 0} means "no threshold" — the metric always passes
 * regardless of the measured figure. Every field defaults to {@code 0} in the
 * auto-configuration, so the feature is fully opt-in: adding even one
 * {@code juke.coverage.threshold.*} key activates gating only for that metric
 * while leaving every other metric unconstrained.
 *
 * <p>Example YAML:
 * <pre>{@code
 * juke:
 *   coverage:
 *     threshold:
 *       server:
 *         line:        80
 *         branch:      60
 *         instruction: 75
 *       ui:
 *         lines:      70
 *         statements: 70
 *         functions:  65
 *         branches:   55
 * }</pre>
 *
 * <p>A CI script needs only inspect the {@code passed} boolean in the JSON
 * response — no percentage parsing required:
 * <pre>{@code
 * curl -sf http://localhost:8080/service/coverage | jq -e '.passed'
 * }</pre>
 *
 * @param serverLine        minimum server line coverage        (0 = unconstrained)
 * @param serverBranch      minimum server branch coverage      (0 = unconstrained)
 * @param serverInstruction minimum server instruction coverage (0 = unconstrained)
 * @param uiLines           minimum UI line coverage            (0 = unconstrained)
 * @param uiStatements      minimum UI statement coverage       (0 = unconstrained)
 * @param uiFunctions       minimum UI function coverage        (0 = unconstrained)
 * @param uiBranches        minimum UI branch coverage          (0 = unconstrained)
 */
public record CoverageThresholds(
        double serverLine,
        double serverBranch,
        double serverInstruction,
        double uiLines,
        double uiStatements,
        double uiFunctions,
        double uiBranches) {

    /** No thresholds configured — every metric always passes. */
    public static final CoverageThresholds NONE =
            new CoverageThresholds(0, 0, 0, 0, 0, 0, 0);

    /**
     * Returns {@code true} when the server-side measurements meet all
     * configured thresholds.
     *
     * @param line        measured line coverage (0–100)
     * @param branch      measured branch coverage (0–100)
     * @param instruction measured instruction coverage (0–100)
     */
    public boolean serverPasses(double line, double branch, double instruction) {
        return line >= serverLine && branch >= serverBranch && instruction >= serverInstruction;
    }

    /**
     * Describes which server-side thresholds were not met. Returns an empty
     * list when all thresholds pass (or no thresholds are configured).
     *
     * @param line        measured line coverage (0–100)
     * @param branch      measured branch coverage (0–100)
     * @param instruction measured instruction coverage (0–100)
     */
    public List<String> serverFailures(double line, double branch, double instruction) {
        List<String> failures = new ArrayList<>();
        if (serverLine > 0 && line < serverLine) {
            failures.add("line %.1f%% < required %.1f%%".formatted(line, serverLine));
        }
        if (serverBranch > 0 && branch < serverBranch) {
            failures.add("branch %.1f%% < required %.1f%%".formatted(branch, serverBranch));
        }
        if (serverInstruction > 0 && instruction < serverInstruction) {
            failures.add("instruction %.1f%% < required %.1f%%"
                    .formatted(instruction, serverInstruction));
        }
        return failures;
    }

    /**
     * Returns {@code true} when the front-end measurements meet all configured
     * thresholds.
     *
     * @param lines      measured line coverage (0–100)
     * @param statements measured statement coverage (0–100)
     * @param functions  measured function coverage (0–100)
     * @param branches   measured branch coverage (0–100)
     */
    public boolean uiPasses(double lines, double statements, double functions, double branches) {
        return lines >= uiLines && statements >= uiStatements
                && functions >= uiFunctions && branches >= uiBranches;
    }

    /**
     * Describes which UI thresholds were not met. Returns an empty list when
     * all thresholds pass (or no thresholds are configured).
     *
     * @param lines      measured line coverage (0–100)
     * @param statements measured statement coverage (0–100)
     * @param functions  measured function coverage (0–100)
     * @param branches   measured branch coverage (0–100)
     */
    public List<String> uiFailures(double lines, double statements,
                                   double functions, double branches) {
        List<String> failures = new ArrayList<>();
        if (uiLines > 0 && lines < uiLines) {
            failures.add("lines %.1f%% < required %.1f%%".formatted(lines, uiLines));
        }
        if (uiStatements > 0 && statements < uiStatements) {
            failures.add("statements %.1f%% < required %.1f%%"
                    .formatted(statements, uiStatements));
        }
        if (uiFunctions > 0 && functions < uiFunctions) {
            failures.add("functions %.1f%% < required %.1f%%"
                    .formatted(functions, uiFunctions));
        }
        if (uiBranches > 0 && branches < uiBranches) {
            failures.add("branches %.1f%% < required %.1f%%"
                    .formatted(branches, uiBranches));
        }
        return failures;
    }
}
