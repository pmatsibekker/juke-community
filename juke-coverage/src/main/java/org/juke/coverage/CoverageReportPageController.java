package org.juke.coverage;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serves a single-page combined coverage drill-down at {@code GET /coverage}.
 *
 * <p>The page hosts two iframes side-by-side — one pointing at the JaCoCo
 * server report, one at the nyc/Istanbul UI report — with a header strip that
 * pulls the live numbers from {@code /service/coverage}. It's intended as a
 * convenient "one URL to open" view for humans, complementing the JSON-only
 * endpoints in {@link CoverageController} and the individual HTML reports
 * served as static resources by {@link CoverageAutoConfiguration}.
 *
 * <p>Implementation note: the HTML is shipped as a classpath resource
 * ({@code juke-coverage-report.html}) rather than templated so the controller
 * has no view-engine dependency and the page is testable on its own.
 */
@Controller
@RequestMapping("/coverage")
public class CoverageReportPageController {

    /** Classpath path of the page template. */
    private static final String PAGE_RESOURCE = "juke-coverage-report.html";

    /**
     * Returns the combined coverage HTML page.
     *
     * <p>Mapped at the root of {@code /coverage} so the URL is short and easy
     * to remember. The existing static resource handlers under
     * {@code /coverage/server/**} and {@code /coverage/ui/**} (registered by
     * {@link CoverageAutoConfiguration}) are unaffected because this mapping
     * is an exact match on {@code /coverage}.
     */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page() throws IOException {
        ClassPathResource res = new ClassPathResource(PAGE_RESOURCE);
        try (InputStream in = res.getInputStream()) {
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        }
    }
}
