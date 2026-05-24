package org.juke.coverage;

import java.time.Instant;

/**
 * Immutable result of reading the front-end (nyc / Istanbul) coverage summary.
 *
 * <p>Serialised straight to JSON by the coverage endpoint. When {@link #available}
 * is {@code false} the percentage fields are zero and {@link #message} explains
 * why — most commonly that no Playwright coverage run has produced a
 * {@code coverage-summary.json} yet.
 *
 * <p>Unlike server coverage (read live in-process), UI coverage is produced
 * out-of-process by {@code nyc} after a Playwright run; this summary therefore
 * reflects the most recently completed coverage run.
 *
 * @param available   whether a coverage summary was found and parsed
 * @param passed      {@code true} when all configured thresholds are met
 *                    (or no thresholds are set); {@code false} when coverage is
 *                    unavailable or a threshold was missed
 * @param message     human-readable status / failure detail — lists every
 *                    metric that fell short of its threshold when
 *                    {@code passed} is {@code false}
 * @param tool        coverage tool identifier ({@code "nyc/Istanbul"})
 * @param lines       line coverage, percent (0–100)
 * @param statements  statement coverage, percent (0–100)
 * @param functions   function coverage, percent (0–100)
 * @param branches    branch coverage, percent (0–100)
 * @param reportUrl   path to the generated drill-down HTML report, or {@code null}
 * @param generatedAt ISO-8601 timestamp of this read
 */
public record UiCoverageSummary(
        boolean available,
        boolean passed,
        String message,
        String tool,
        double lines,
        double statements,
        double functions,
        double branches,
        String reportUrl,
        String generatedAt) {

    /** Builds an "unavailable" summary carrying only a status message. */
    static UiCoverageSummary unavailable(String message) {
        return new UiCoverageSummary(false, false, message, "nyc/Istanbul",
                0.0, 0.0, 0.0, 0.0, null, Instant.now().toString());
    }
}
