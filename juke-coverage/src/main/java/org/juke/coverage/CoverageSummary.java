package org.juke.coverage;

import java.time.Instant;
import java.util.List;

/**
 * Immutable result of a server-side functional-coverage report run.
 *
 * <p>Serialised straight to JSON by the coverage endpoint. When {@link #available}
 * is {@code false} the percentage fields are zero and {@link #message} explains
 * why (most commonly: the JaCoCo agent was not attached to the server JVM).
 *
 * @param available       whether a coverage report was produced
 * @param passed          {@code true} when all configured thresholds are met
 *                        (or no thresholds are set); {@code false} when
 *                        coverage is unavailable or a threshold was missed
 * @param message         human-readable status / failure detail — lists every
 *                        metric that fell short of its threshold when
 *                        {@code passed} is {@code false}
 * @param tool            coverage tool identifier ({@code "JaCoCo"})
 * @param instruction     instruction coverage, percent (0–100)
 * @param branch          branch coverage, percent (0–100)
 * @param line            line coverage, percent (0–100)
 * @param analyzedClasses number of application classes counted (after exclusions)
 * @param excludedSeams   the {@code @Juke} seams excluded from the figure, each
 *                        formatted {@code "interfaceFqn -> implementationFqn"}
 * @param reportUrl       path to the generated drill-down HTML report, or {@code null}
 * @param generatedAt     ISO-8601 timestamp of this run
 */
public record CoverageSummary(
        boolean available,
        boolean passed,
        String message,
        String tool,
        double instruction,
        double branch,
        double line,
        int analyzedClasses,
        List<String> excludedSeams,
        String reportUrl,
        String generatedAt) {

    /** Builds an "unavailable" summary carrying only a status message. */
    static CoverageSummary unavailable(String message) {
        return new CoverageSummary(false, false, message, "JaCoCo",
                0.0, 0.0, 0.0, 0, List.of(), null, Instant.now().toString());
    }
}
