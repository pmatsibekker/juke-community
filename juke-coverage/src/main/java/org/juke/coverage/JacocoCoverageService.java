package org.juke.coverage;

import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.ISourceFileLocator;
import org.jacoco.report.MultiSourceFileLocator;
import org.jacoco.report.html.HTMLFormatter;
import org.juke.framework.coverage.JukeMockRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Produces a functional code-coverage report for the application under test by
 * reading the JaCoCo agent <em>in-process</em>.
 *
 * <p>The server JVM is launched with {@code -javaagent:jacoco-agent.jar}; this
 * service then calls {@link RT#getAgent()} to pull the live execution data,
 * analyses it against the application's compiled classes, and renders a
 * drill-down HTML report. Because the agent accumulates coverage for the whole
 * JVM lifetime, every Juke replay session driven against the server adds to the
 * same figure — multi-run aggregation is automatic.
 *
 * <p>Classes that Juke has displaced with a record/replay proxy are excluded:
 * in replay mode they are never executed, so counting them would unfairly
 * depress the result. The exclusion set comes straight from
 * {@link JukeMockRegistry} — the developer never names an implementation class.
 *
 * <p>Every failure mode (no agent attached, classes directory missing, I/O
 * error) is reported as an {@linkplain CoverageSummary#unavailable unavailable}
 * summary rather than thrown — a coverage hiccup must never break the host app.
 */
public class JacocoCoverageService {

    private static final Logger LOG = LoggerFactory.getLogger(JacocoCoverageService.class);

    private final String classesDir;
    private final String sourcesDir;
    private final String reportDir;
    private final String bundleName;
    private final CoverageThresholds thresholds;

    /**
     * @param classesDir absolute path to the application's compiled classes
     *                   ({@code target/classes}); coverage is unavailable if blank/missing
     * @param sourcesDir absolute path to the application sources ({@code src/main/java});
     *                   blank disables source highlighting but the report still renders
     * @param reportDir  directory the HTML report is written into
     * @param bundleName display name for the coverage bundle
     * @param thresholds minimum coverage percentages; use {@link CoverageThresholds#NONE}
     *                   when no thresholds are configured
     */
    public JacocoCoverageService(String classesDir, String sourcesDir,
                                 String reportDir, String bundleName,
                                 CoverageThresholds thresholds) {
        this.classesDir = classesDir == null ? "" : classesDir.trim();
        this.sourcesDir = sourcesDir == null ? "" : sourcesDir.trim();
        this.reportDir = reportDir;
        this.bundleName = bundleName == null || bundleName.isBlank()
                ? "Application under test" : bundleName;
        this.thresholds = thresholds != null ? thresholds : CoverageThresholds.NONE;
    }

    /** Directory the HTML report is written into (also served as a static route). */
    public String reportDirectory() {
        return reportDir;
    }

    /**
     * Pulls live coverage from the JaCoCo agent, analyses the application
     * classes, writes the HTML report, and returns a summary. Never throws.
     *
     * @param reportUrlPath the public URL path the rendered report is served at
     * @return a populated {@link CoverageSummary}, or an unavailable one on any failure
     */
    public synchronized CoverageSummary generateReport(String reportUrlPath) {
        try {
            return doGenerate(reportUrlPath);
        } catch (Throwable t) {
            LOG.warn("Coverage report generation failed", t);
            return CoverageSummary.unavailable("Coverage report failed: " + t.getMessage());
        }
    }

    private CoverageSummary doGenerate(String reportUrlPath) throws Exception {
        // ── 1. Read execution data from the live agent ─────────────────────────
        byte[] execData;
        try {
            IAgent agent = RT.getAgent();
            execData = agent.getExecutionData(false); // false = snapshot, do not reset
        } catch (Throwable noAgent) {
            return CoverageSummary.unavailable(
                    "JaCoCo agent not attached — start the server with "
                            + "-javaagent:jacoco-agent.jar to enable coverage.");
        }

        // ── 2. Validate the application classes directory ──────────────────────
        if (classesDir.isEmpty()) {
            return CoverageSummary.unavailable(
                    "juke.coverage.classes is not set — point it at the "
                            + "application's target/classes directory.");
        }
        Path classesRoot = new File(classesDir).toPath();
        if (!Files.isDirectory(classesRoot)) {
            return CoverageSummary.unavailable(
                    "juke.coverage.classes directory not found: " + classesDir);
        }

        // ── 3. Parse the execution data ────────────────────────────────────────
        ExecFileLoader loader = new ExecFileLoader();
        loader.load(new ByteArrayInputStream(execData));
        ExecutionDataStore execStore = loader.getExecutionDataStore();
        SessionInfoStore sessionStore = loader.getSessionInfoStore();

        // ── 4. Analyse application classes, skipping @Juke-mocked impls ────────
        Set<String> excludedClasses = JukeMockRegistry.getMockedImplementationClassNames();
        CoverageBuilder builder = new CoverageBuilder();
        Analyzer analyzer = new Analyzer(execStore, builder);

        List<Path> classFiles;
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
        }

        int skipped = 0;
        for (Path file : classFiles) {
            if (isExcluded(classNameOf(classesRoot, file), excludedClasses)) {
                skipped++;
                continue;
            }
            analyzer.analyzeClass(Files.readAllBytes(file), file.toString());
        }

        IBundleCoverage bundle = builder.getBundle(bundleName);

        // ── 5. Render the drill-down HTML report ───────────────────────────────
        File outDir = new File(reportDir);
        Files.createDirectories(outDir.toPath());
        HTMLFormatter formatter = new HTMLFormatter();
        IReportVisitor visitor = formatter.createVisitor(new FileMultiReportOutput(outDir));
        visitor.visitInfo(sessionStore.getInfos(), execStore.getContents());
        visitor.visitBundle(bundle, sourceLocator());
        visitor.visitEnd();

        // ── 6. Assemble the summary ────────────────────────────────────────────
        List<String> seams = new ArrayList<>();
        for (Map.Entry<String, String> e
                : JukeMockRegistry.getMockedImplementationsByInterface().entrySet()) {
            seams.add(e.getKey() + " -> " + e.getValue());
        }

        double linePct        = percent(bundle.getLineCounter());
        double branchPct      = percent(bundle.getBranchCounter());
        double instructionPct = percent(bundle.getInstructionCounter());

        List<String> failures = thresholds.serverFailures(linePct, branchPct, instructionPct);
        boolean passed = failures.isEmpty();
        String message = passed
                ? "Coverage generated from live JaCoCo agent data."
                : "Coverage below threshold — " + String.join(", ", failures) + ".";

        LOG.info("Coverage report: {} classes analysed, {} @Juke-mocked classes excluded, passed={}",
                bundle.getClassCounter().getTotalCount(), skipped, passed);

        return new CoverageSummary(
                true,
                passed,
                message,
                "JaCoCo",
                instructionPct,
                branchPct,
                linePct,
                bundle.getClassCounter().getTotalCount(),
                seams,
                reportUrlPath,
                Instant.now().toString());
    }

    /** Source locator for HTML line highlighting; degrades gracefully if sources are absent. */
    private ISourceFileLocator sourceLocator() {
        if (!sourcesDir.isEmpty() && Files.isDirectory(new File(sourcesDir).toPath())) {
            return new DirectorySourceFileLocator(new File(sourcesDir), "utf-8", 4);
        }
        return new MultiSourceFileLocator(4); // empty — report renders without source view
    }

    /** Derives a fully-qualified class name from a {@code .class} file path. */
    private static String classNameOf(Path classesRoot, Path classFile) {
        String relative = classesRoot.relativize(classFile).toString();
        return relative
                .substring(0, relative.length() - ".class".length())
                .replace(File.separatorChar, '.')
                .replace('/', '.');
    }

    /**
     * A class is excluded if it is a mocked implementation itself, or a nested
     * class of one ({@code Outer$Inner}).
     */
    private static boolean isExcluded(String className, Set<String> excluded) {
        for (String ex : excluded) {
            if (className.equals(ex) || className.startsWith(ex + "$")) {
                return true;
            }
        }
        return false;
    }

    /** Coverage ratio of a counter as a percentage rounded to two decimals. */
    private static double percent(ICounter counter) {
        if (counter.getTotalCount() == 0) {
            return 0.0;
        }
        return Math.round(counter.getCoveredRatio() * 10_000.0) / 100.0;
    }
}
