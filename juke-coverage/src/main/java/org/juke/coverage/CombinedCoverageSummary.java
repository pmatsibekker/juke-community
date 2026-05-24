package org.juke.coverage;

import java.time.Instant;

/**
 * Combined functional coverage summary — server-side (JaCoCo) and front-end
 * (nyc / Istanbul) measurements in a single response.
 *
 * <p>Serialised straight to JSON by {@code GET /service/coverage}. Both
 * sub-summaries carry their own {@link CoverageSummary#available} /
 * {@link UiCoverageSummary#available} flag, so a caller can determine at a
 * glance whether either half is missing without inspecting nested fields.
 *
 * <p>Example response:
 * <pre>{@code
 * {
 *   "server": {
 *     "available": true,
 *     "passed": true,
 *     "tool": "JaCoCo",
 *     "instruction": 84.2,
 *     "branch": 71.0,
 *     "line": 86.5,
 *     "analyzedClasses": 14,
 *     "excludedSeams": ["com.example.IGreetingsService -> com.example.GreetingServiceImpl"],
 *     "reportUrl": "/coverage/server/index.html",
 *     "generatedAt": "2026-05-20T10:00:00Z"
 *   },
 *   "ui": {
 *     "available": true,
 *     "passed": true,
 *     "tool": "nyc/Istanbul",
 *     "lines": 78.0,
 *     "statements": 77.4,
 *     "functions": 80.0,
 *     "branches": 62.5,
 *     "reportUrl": "/coverage/ui/index.html",
 *     "generatedAt": "2026-05-20T10:00:00Z"
 *   },
 *   "passed": true,
 *   "generatedAt": "2026-05-20T10:00:00Z"
 * }
 * }</pre>
 *
 * @param server      server-side JaCoCo coverage, or an unavailable summary
 *                    when the agent is not attached / classes directory is missing
 * @param ui          front-end nyc/Istanbul coverage, or an unavailable summary
 *                    when no Playwright run has produced a {@code coverage-summary.json}
 * @param passed      {@code true} when both halves pass their configured thresholds;
 *                    {@code false} when either half is unavailable or below threshold.
 *                    CI scripts that only have server coverage should gate on
 *                    {@code GET /service/coverage/server} instead.
 * @param generatedAt ISO-8601 timestamp of this combined read
 */
public record CombinedCoverageSummary(
        CoverageSummary server,
        UiCoverageSummary ui,
        boolean passed,
        String generatedAt) {

    /**
     * Convenience factory — wraps both halves, derives the combined
     * {@code passed} flag, and timestamps them together so the top-level
     * {@code generatedAt} reflects when the combined call was made rather than
     * each half's individual timestamp.
     *
     * @param server server coverage summary (never {@code null})
     * @param ui     UI coverage summary (never {@code null})
     * @return a new {@link CombinedCoverageSummary}
     */
    static CombinedCoverageSummary of(CoverageSummary server, UiCoverageSummary ui) {
        return new CombinedCoverageSummary(server, ui,
                server.passed() && ui.passed(), Instant.now().toString());
    }
}
