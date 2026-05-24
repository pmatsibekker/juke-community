package org.juke.remix.aspect;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.juke.framework.events.ReplayContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bridges {@code X-Juke-Run-Id} / {@code X-Juke-Use-Case-Id} request headers
 * (set by the Phase 4 generated Playwright specs) into the framework's
 * {@link ReplayContext} ThreadLocal, so the {@code @JukeController} advice
 * and the proxy hooks can attribute their events to the right run + use case.
 *
 * <p>The filter is ordered to run early in the chain so the scope is in place
 * by the time controller advice or service-level proxies execute. It always
 * clears the context in {@code finally}, even on exceptions, to prevent leaks
 * between requests sharing a thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JukeReplayContextFilter extends OncePerRequestFilter {

    public static final String HEADER_RUN_ID       = "X-Juke-Run-Id";
    public static final String HEADER_USE_CASE_ID  = "X-Juke-Use-Case-Id";
    public static final String HEADER_RECORDING_ID = "X-Juke-Recording-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Long runId       = parseLong(request.getHeader(HEADER_RUN_ID));
        Long useCaseId   = parseLong(request.getHeader(HEADER_USE_CASE_ID));
        Long recordingId = parseLong(request.getHeader(HEADER_RECORDING_ID));

        boolean activated = runId != null || useCaseId != null || recordingId != null;
        if (activated) {
            ReplayContext.set(new ReplayContext.Scope(runId, useCaseId, recordingId));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            if (activated) ReplayContext.clear();
        }
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return null; }
    }
}
