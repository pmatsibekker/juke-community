package org.juke.framework.coverage;

import java.util.Map;

/**
 * SPI that lets modules providing coverage data (e.g. {@code juke-coverage})
 * contribute a coverage snapshot to the Juke recording/replay report without
 * creating a compile-time dependency from {@code juke-remix-rest-service} on
 * {@code juke-coverage}.
 *
 * <p>Discovered via Spring's optional injection
 * ({@code @Autowired(required = false)}) in the report controller. When
 * {@code juke-coverage} is on the classpath and
 * {@code juke.coverage.enabled=true}, its auto-configuration registers an
 * implementation; otherwise the report simply omits the {@code "coverage"}
 * section rather than failing.
 *
 * <p>The interface lives in {@code juke-framework} so both {@code juke-coverage}
 * (implementer) and {@code juke-remix-rest-service} (consumer) can reference it
 * through their existing shared dependency without any new dependency edge.
 */
public interface CoverageContributor {

    /**
     * Returns a snapshot of the current coverage as a serialisation-ready
     * plain {@link Map}. The map is embedded verbatim into the
     * {@code GET /service/recording/report} JSON under the key
     * {@code "coverage"} at the top of the report.
     *
     * <p>Must never throw — implementations must handle a missing JaCoCo agent,
     * an absent nyc summary, or any other unavailable-data condition by
     * returning a map with {@code "available": false} rather than propagating
     * an exception.
     *
     * @return a map suitable for direct JSON serialisation; never {@code null}
     */
    Map<String, Object> coverageSnapshot();
}
