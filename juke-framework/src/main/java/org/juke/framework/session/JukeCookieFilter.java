package org.juke.framework.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet filter that reads Juke playback cookies from every incoming HTTP
 * request and populates the request-scoped {@link JukeSessionContext}.
 * <p>
 * The filter is auto-registered as a Spring bean and fires before the
 * {@code DispatcherServlet}. It does nothing (calls {@code chain.doFilter}
 * immediately) if the cookies are absent &mdash; non-test requests are
 * completely unaffected with zero overhead beyond the cookie lookup.
 * <p>
 * <b>Cookie protocol:</b>
 * <ul>
 *   <li>{@code JUKE_SESSION_ID} &mdash; UUID identifying the test session</li>
 *   <li>{@code JUKE_TRACK} &mdash; name of the ZIP archive to replay from</li>
 * </ul>
 */
public class JukeCookieFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(JukeCookieFilter.class);

    public static final String COOKIE_SESSION_ID = "JUKE_SESSION_ID";
    public static final String COOKIE_TRACK = "JUKE_TRACK";

    /** MDC keys populated for the duration of a Juke-session request. */
    public static final String MDC_SESSION_ID = "jukeSessionId";
    public static final String MDC_TRACK = "jukeTrack";

    private final ApplicationContext applicationContext;
    private final SessionRegistry registry;

    public JukeCookieFilter(ApplicationContext applicationContext, SessionRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String sessionId = getCookieValue(request, COOKIE_SESSION_ID);
        String trackName = getCookieValue(request, COOKIE_TRACK);

        boolean mdcPopulated = false;
        if (sessionId != null && trackName != null && registry.isValid(sessionId, trackName)) {
            try {
                JukeSessionContext ctx = applicationContext.getBean(JukeSessionContext.class);
                ctx.setSessionId(sessionId);
                ctx.setTrackName(trackName);
                ctx.setPlaybackActive(true);
                MDC.put(MDC_SESSION_ID, sessionId);
                MDC.put(MDC_TRACK, trackName);
                mdcPopulated = true;
                LOG.debug("Juke session active: {} (track '{}')", sessionId, trackName);
            } catch (Exception e) {
                // Should not happen in a web request context, but be defensive
                LOG.warn("Failed to populate JukeSessionContext: {}", e.getMessage());
            }
        }

        try {
            chain.doFilter(request, response);
        } finally {
            if (mdcPopulated) {
                MDC.remove(MDC_SESSION_ID);
                MDC.remove(MDC_TRACK);
            }
        }
    }

    /**
     * Extracts a cookie value from the request by name.
     *
     * @param request the HTTP request
     * @param name    the cookie name
     * @return the cookie value, or {@code null} if not found
     */
    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
