package org.juke.coverage;

import org.juke.framework.coverage.CoverageContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opt-in auto-configuration for Juke server-side functional coverage.
 *
 * <p>Every bean here is gated on {@code juke.coverage.enabled=true}. When the
 * property is absent or {@code false} — the default, and the expected state in
 * production — none of these beans are created and the coverage feature has no
 * runtime footprint beyond a single condition evaluation at startup. A real
 * application keeps {@code juke-coverage} on the classpath across all
 * environments and simply sets the property per environment (typically
 * {@code true} in {@code application-local.yml} / {@code application-uat.yml},
 * unset in production).
 *
 * <p>The actual instrumentation overhead is governed independently by the
 * {@code -javaagent:jacoco-agent.jar} JVM flag, which is likewise present only
 * in local / UAT launch commands. If the property is on but no agent is
 * attached, the service degrades gracefully and reports coverage as unavailable.
 *
 * <p>Configurable properties:
 * <p>Endpoints exposed:
 * <ul>
 *   <li>{@code GET /service/coverage} — combined server + UI summary</li>
 *   <li>{@code GET /service/coverage/server} — server-side JaCoCo summary</li>
 *   <li>{@code GET /service/coverage/ui} — front-end nyc/Istanbul summary</li>
 * </ul>
 *
 * <p>Configurable properties:
 * <ul>
 *   <li>{@code juke.coverage.classes} — application {@code target/classes} directory</li>
 *   <li>{@code juke.coverage.sources} — application {@code src/main/java} directory (optional)</li>
 *   <li>{@code juke.coverage.report-dir} — where the server HTML report is written</li>
 *   <li>{@code juke.coverage.bundle-name} — display name for the coverage bundle</li>
 *   <li>{@code juke.coverage.ui-report-dir} — where the Playwright run writes the
 *       nyc/Istanbul UI report (read by {@code /service/coverage/ui})</li>
 *   <li>{@code juke.coverage.threshold.server.line} — minimum server line coverage (0 = off)</li>
 *   <li>{@code juke.coverage.threshold.server.branch} — minimum server branch coverage (0 = off)</li>
 *   <li>{@code juke.coverage.threshold.server.instruction} — minimum server instruction coverage (0 = off)</li>
 *   <li>{@code juke.coverage.threshold.ui.lines} — minimum UI line coverage (0 = off)</li>
 *   <li>{@code juke.coverage.threshold.ui.statements} — minimum UI statement coverage (0 = off)</li>
 *   <li>{@code juke.coverage.threshold.ui.functions} — minimum UI function coverage (0 = off)</li>
 *   <li>{@code juke.coverage.threshold.ui.branches} — minimum UI branch coverage (0 = off)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "juke.coverage.enabled", havingValue = "true")
public class CoverageAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(CoverageAutoConfiguration.class);

    @Bean
    public CoverageThresholds coverageThresholds(
            @Value("${juke.coverage.threshold.server.line:0}")        double serverLine,
            @Value("${juke.coverage.threshold.server.branch:0}")      double serverBranch,
            @Value("${juke.coverage.threshold.server.instruction:0}") double serverInstruction,
            @Value("${juke.coverage.threshold.ui.lines:0}")           double uiLines,
            @Value("${juke.coverage.threshold.ui.statements:0}")      double uiStatements,
            @Value("${juke.coverage.threshold.ui.functions:0}")       double uiFunctions,
            @Value("${juke.coverage.threshold.ui.branches:0}")        double uiBranches) {
        CoverageThresholds t = new CoverageThresholds(
                serverLine, serverBranch, serverInstruction,
                uiLines, uiStatements, uiFunctions, uiBranches);
        LOG.info("Juke coverage thresholds — server(line={}, branch={}, instruction={}), "
                + "ui(lines={}, statements={}, functions={}, branches={})",
                serverLine, serverBranch, serverInstruction,
                uiLines, uiStatements, uiFunctions, uiBranches);
        return t;
    }

    @Bean
    public JacocoCoverageService jacocoCoverageService(
            @Value("${juke.coverage.classes:}") String classesDir,
            @Value("${juke.coverage.sources:}") String sourcesDir,
            @Value("${juke.coverage.report-dir:${user.home}/juke-demo/coverage/server}") String reportDir,
            @Value("${juke.coverage.bundle-name:}") String bundleName,
            CoverageThresholds thresholds) {
        LOG.info("Juke coverage enabled — classes='{}', report-dir='{}'", classesDir, reportDir);
        return new JacocoCoverageService(classesDir, sourcesDir, reportDir, bundleName, thresholds);
    }

    @Bean
    public UiCoverageService uiCoverageService(
            @Value("${juke.coverage.ui-report-dir:${user.home}/juke-demo/coverage/ui}") String uiReportDir,
            CoverageThresholds thresholds) {
        LOG.info("Juke UI coverage — reading nyc summary from '{}'", uiReportDir);
        return new UiCoverageService(uiReportDir, thresholds);
    }

    @Bean
    public CoverageController coverageController(JacocoCoverageService jacocoCoverageService,
                                                 UiCoverageService uiCoverageService) {
        return new CoverageController(jacocoCoverageService, uiCoverageService);
    }

    /**
     * Serves the combined HTML drill-down page at {@code GET /coverage}.
     * Declared as a bean (not picked up via component scanning) so it inherits
     * the {@code juke.coverage.enabled=true} gate on this auto-configuration.
     */
    @Bean
    public CoverageReportPageController coverageReportPageController() {
        return new CoverageReportPageController();
    }

    /**
     * Contributes a live coverage snapshot to {@code GET /service/recording/report}
     * without requiring {@code juke-remix-rest-service} to depend on
     * {@code juke-coverage}. The report controller discovers this bean via
     * {@code @Autowired(required = false)} and embeds the snapshot at the top of the
     * report JSON under the key {@code "coverage"}.
     *
     * <p>The snapshot is taken at the moment the report endpoint is called:
     * server coverage reflects everything exercised since the JVM started (including
     * the replay run just completed), and UI coverage reflects the most recent nyc
     * report generated by the Playwright coverage fixture.
     */
    @Bean
    public CoverageContributor coverageContributor(JacocoCoverageService jacoco,
                                                   UiCoverageService ui) {
        return () -> {
            CoverageSummary server =
                    jacoco.generateReport(CoverageController.SERVER_REPORT_URL_PATH);
            UiCoverageSummary uiSummary =
                    ui.readSummary(CoverageController.UI_REPORT_URL_PATH);

            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("passed",      server.passed() && uiSummary.passed());
            snapshot.put("generatedAt", Instant.now().toString());
            snapshot.put("server",      serverSection(server));
            snapshot.put("ui",          uiSection(uiSummary));
            return snapshot;
        };
    }

    private static Map<String, Object> serverSection(CoverageSummary s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", s.available());
        m.put("passed",    s.passed());
        m.put("message",   s.message());
        if (s.available()) {
            m.put("line",           s.line());
            m.put("branch",         s.branch());
            m.put("instruction",    s.instruction());
            m.put("analyzedClasses", s.analyzedClasses());
            if (!s.excludedSeams().isEmpty()) {
                m.put("excludedSeams", s.excludedSeams());
            }
            // Link to the drill-down HTML report so callers can open it directly.
            if (s.reportUrl() != null) {
                m.put("reportUrl", s.reportUrl());
            }
        }
        return m;
    }

    private static Map<String, Object> uiSection(UiCoverageSummary u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("available", u.available());
        m.put("passed",    u.passed());
        m.put("message",   u.message());
        if (u.available()) {
            m.put("lines",      u.lines());
            m.put("statements", u.statements());
            m.put("functions",  u.functions());
            m.put("branches",   u.branches());
            // Link to the drill-down HTML report so callers can open it directly.
            if (u.reportUrl() != null) {
                m.put("reportUrl", u.reportUrl());
            }
        }
        return m;
    }

    /**
     * Serves the generated coverage HTML reports as static routes so the report
     * links are openable from a browser on the same origin (a {@code file://}
     * link cannot be followed from an {@code http://} page):
     * <ul>
     *   <li>{@code /coverage/server/**} — the in-process JaCoCo report</li>
     *   <li>{@code /coverage/ui/**} — the nyc/Istanbul report from Playwright</li>
     * </ul>
     */
    @Bean
    public WebMvcConfigurer jukeCoverageResourceConfigurer(JacocoCoverageService jacocoCoverageService,
                                                           UiCoverageService uiCoverageService) {
        String serverLocation = toFileUrl(jacocoCoverageService.reportDirectory());
        String uiLocation = toFileUrl(uiCoverageService.reportDirectory());
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/coverage/server/**")
                        .addResourceLocations(serverLocation);
                registry.addResourceHandler("/coverage/ui/**")
                        .addResourceLocations(uiLocation);
            }
        };
    }

    /** Builds a {@code file:} resource location with forward slashes and a trailing slash. */
    private static String toFileUrl(String directory) {
        String normalised = directory.replace('\\', '/');
        if (!normalised.endsWith("/")) {
            normalised = normalised + "/";
        }
        return "file:" + normalised;
    }
}
