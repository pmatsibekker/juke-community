package org.juke.coverage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for functional coverage.
 *
 * <ul>
 *   <li>{@code GET /service/coverage} — combined server + UI summary in one
 *       response; useful for dashboard widgets and CI one-call checks.</li>
 *   <li>{@code GET /service/coverage/server} — server-side (JaCoCo) coverage
 *       of the application under test, generated live in-process.</li>
 *   <li>{@code GET /service/coverage/ui} — front-end (nyc/Istanbul) coverage,
 *       read from the latest Playwright coverage run.</li>
 * </ul>
 *
 * <p>All endpoints always respond {@code 200} — coverage being unavailable
 * (no agent attached, no Playwright run yet) is reported in the body via the
 * {@code available} flag, not as an HTTP error. The drill-down HTML reports
 * are served as static routes (see {@link CoverageAutoConfiguration}) so the
 * {@code reportUrl} in each summary is openable directly in the browser.
 */
@RestController
@RequestMapping("/service/coverage")
public class CoverageController {

    /** Public path the server-side HTML report is served at. */
    static final String SERVER_REPORT_URL_PATH = "/coverage/server/index.html";

    /** Public path the UI HTML report is served at. */
    static final String UI_REPORT_URL_PATH = "/coverage/ui/index.html";

    private final JacocoCoverageService serverCoverageService;
    private final UiCoverageService uiCoverageService;

    public CoverageController(JacocoCoverageService serverCoverageService,
                              UiCoverageService uiCoverageService) {
        this.serverCoverageService = serverCoverageService;
        this.uiCoverageService = uiCoverageService;
    }

    /**
     * Returns both server-side and front-end coverage summaries in a single
     * response — useful for dashboard widgets and CI one-call checks that need
     * both figures without making two sequential requests.
     *
     * <p>Both halves carry their own {@code available} flag, so a caller can
     * determine at a glance whether either measurement is missing.
     *
     * @return a {@link CombinedCoverageSummary} containing the server and UI summaries
     */
    @GetMapping
    public CombinedCoverageSummary combined() {
        return CombinedCoverageSummary.of(
                serverCoverageService.generateReport(SERVER_REPORT_URL_PATH),
                uiCoverageService.readSummary(UI_REPORT_URL_PATH));
    }

    /**
     * Generates and returns the current server-side coverage summary.
     *
     * @return the coverage summary; {@code available=false} if none could be produced
     */
    @GetMapping("/server")
    public CoverageSummary serverCoverage() {
        return serverCoverageService.generateReport(SERVER_REPORT_URL_PATH);
    }

    /**
     * Returns the front-end coverage summary from the latest Playwright run.
     *
     * @return the UI coverage summary; {@code available=false} if none has been produced
     */
    @GetMapping("/ui")
    public UiCoverageSummary uiCoverage() {
        return uiCoverageService.readSummary(UI_REPORT_URL_PATH);
    }
}
