package org.juke.coverage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Surfaces front-end functional coverage produced by the Playwright run.
 *
 * <p>The browser-side coverage pipeline lives entirely outside the JVM: the
 * greeting SPA is built instrumented (babel-plugin-istanbul, via the
 * {@code -Pcoverage} Maven profile), Playwright's coverage fixture harvests
 * {@code window.__coverage__}, and the run's global teardown invokes {@code nyc}
 * to render an HTML report plus a {@code coverage-summary.json} into the UI
 * report directory.
 *
 * <p>This service simply reads that {@code coverage-summary.json} — it does not
 * generate anything. It therefore reflects the most recently completed
 * Playwright coverage run, and reports {@linkplain UiCoverageSummary#unavailable
 * unavailable} (never throws) when no summary file is present yet.
 */
public class UiCoverageService {

    private static final Logger LOG = LoggerFactory.getLogger(UiCoverageService.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String uiReportDir;
    private final CoverageThresholds thresholds;

    /**
     * @param uiReportDir directory the Playwright teardown writes the nyc report
     *                    into (HTML report + {@code coverage-summary.json})
     * @param thresholds  minimum coverage percentages; use
     *                    {@link CoverageThresholds#NONE} when no thresholds are configured
     */
    public UiCoverageService(String uiReportDir, CoverageThresholds thresholds) {
        this.uiReportDir = uiReportDir;
        this.thresholds = thresholds != null ? thresholds : CoverageThresholds.NONE;
    }

    /** Directory the UI HTML report lives in (also served as a static route). */
    public String reportDirectory() {
        return uiReportDir;
    }

    /**
     * Reads and parses the latest nyc coverage summary. Never throws.
     *
     * @param reportUrlPath the public URL path the rendered HTML report is served at
     * @return a populated {@link UiCoverageSummary}, or an unavailable one if no
     *         summary exists or it cannot be parsed
     */
    public UiCoverageSummary readSummary(String reportUrlPath) {
        File summaryFile = new File(uiReportDir, "coverage-summary.json");
        if (!summaryFile.isFile()) {
            return UiCoverageSummary.unavailable(
                    "No UI coverage summary at " + summaryFile
                            + " — run the Playwright coverage spec (project \"Coverage\") first.");
        }
        try {
            JsonNode total = MAPPER.readTree(summaryFile).path("total");
            if (total.isMissingNode() || total.isEmpty()) {
                return UiCoverageSummary.unavailable(
                        "coverage-summary.json has no 'total' section.");
            }
            double lines      = pct(total, "lines");
            double statements = pct(total, "statements");
            double functions  = pct(total, "functions");
            double branches   = pct(total, "branches");

            java.util.List<String> failures =
                    thresholds.uiFailures(lines, statements, functions, branches);
            boolean passed = failures.isEmpty();
            String message = passed
                    ? "UI coverage read from the latest nyc/Istanbul summary."
                    : "UI coverage below threshold — "
                            + String.join(", ", failures) + ".";

            return new UiCoverageSummary(
                    true,
                    passed,
                    message,
                    "nyc/Istanbul",
                    lines,
                    statements,
                    functions,
                    branches,
                    reportUrlPath,
                    java.time.Instant.now().toString());
        } catch (Exception e) {
            LOG.warn("Failed to read UI coverage summary {}", summaryFile, e);
            return UiCoverageSummary.unavailable(
                    "Failed to read UI coverage summary: " + e.getMessage());
        }
    }

    /** Reads {@code total.<metric>.pct} from an nyc json-summary node. */
    private static double pct(JsonNode total, String metric) {
        return total.path(metric).path("pct").asDouble(0.0);
    }
}
