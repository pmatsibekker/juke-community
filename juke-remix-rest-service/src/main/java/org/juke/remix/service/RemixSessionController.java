package org.juke.remix.service;

import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.session.JukeCookieFilter;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller exposing the Juke per-session playback lifecycle to
 * Playwright (and other HTTP clients).
 * <p>
 * <b>Endpoints:</b>
 * <ul>
 *     <li>{@code GET /service/session/start?track=<name>} &mdash; creates a
 *         new playback session, validates the track ZIP exists, and sets
 *         {@code JUKE_SESSION_ID} + {@code JUKE_TRACK} cookies on the
 *         response.</li>
 *     <li>{@code GET /service/session/stop} &mdash; invalidates the session
 *         and clears the cookies.</li>
 *     <li>{@code GET /service/session/status} &mdash; returns the current
 *         session info (useful for Playwright assertions).</li>
 * </ul>
 * <p>
 * The {@code /start} endpoint is the Playwright test's first call. After it
 * returns, the cookies are in the browser's cookie jar and will be sent
 * automatically with every subsequent request to the same origin.
 */
@RestController
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
@RequestMapping("/service/session")
public class RemixSessionController {

    private static final Logger log = LoggerFactory.getLogger(RemixSessionController.class);

    private static final int COOKIE_MAX_AGE = 3600; // 1 hour

    @Autowired
    private SessionRegistry registry;

    /**
     * Starts a new Juke playback session for the given track.
     * <p>
     * Validates that the track ZIP file exists, creates a
     * {@link JukeSessionEntry}, and sets the session cookies on the response.
     *
     * @param track the recording name (e.g., "login-happy-path")
     * @return 200 OK with session JSON, or 404 if the track ZIP is not found
     */
    @GetMapping("/start")
    public ResponseEntity<Map<String, String>> startSession(
            @RequestParam("track") String track,
            @RequestParam(value = "description", required = false) String description,
            HttpServletResponse response) {

        if (track == null || track.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validate track name (alphanumeric + hyphens + underscores only)
        String sanitized = track.trim();
        if (!sanitized.matches("^[\\w\\-]+$")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            JukeSessionEntry entry = registry.create(sanitized, description);

            // Set cookies on the response
            ResponseCookie sessionCookie = ResponseCookie
                    .from(JukeCookieFilter.COOKIE_SESSION_ID, entry.getSessionId())
                    .httpOnly(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(COOKIE_MAX_AGE)
                    .build();

            ResponseCookie trackCookie = ResponseCookie
                    .from(JukeCookieFilter.COOKIE_TRACK, sanitized)
                    .httpOnly(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(COOKIE_MAX_AGE)
                    .build();

            response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
            response.addHeader(HttpHeaders.SET_COOKIE, trackCookie.toString());

            Map<String, String> body = new LinkedHashMap<>();
            body.put("sessionId", entry.getSessionId());
            body.put("track", sanitized);
            body.put("status", "active");

            log.info("Started Juke session {} for track '{}'", entry.getSessionId(), sanitized);
            return ResponseEntity.ok(body);

        } catch (JukeAccessException e) {
            log.warn("Failed to start session for track '{}': {}", sanitized, e.getMessage());
            Map<String, String> error = new LinkedHashMap<>();
            error.put("error", "Track not found: " + sanitized);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * Stops the current Juke playback session.
     * <p>
     * Reads the {@code JUKE_SESSION_ID} cookie from the request, invalidates
     * the session in the registry, and clears both cookies.
     */
    @GetMapping("/stop")
    public ResponseEntity<Map<String, String>> stopSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        String sessionId = getCookieValue(request, JukeCookieFilter.COOKIE_SESSION_ID);

        if (sessionId != null) {
            registry.invalidate(sessionId);
        }

        // Expire both cookies
        ResponseCookie expireSession = ResponseCookie
                .from(JukeCookieFilter.COOKIE_SESSION_ID, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie expireTrack = ResponseCookie
                .from(JukeCookieFilter.COOKIE_TRACK, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, expireSession.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, expireTrack.toString());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "stopped");
        body.put("sessionId", sessionId != null ? sessionId : "none");

        log.info("Stopped Juke session {}", sessionId);
        return ResponseEntity.ok(body);
    }

    /**
     * Returns information about the current Juke session (if any).
     * <p>
     * Useful for Playwright assertions to verify that a session is active.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, String>> sessionStatus(HttpServletRequest request) {
        String sessionId = getCookieValue(request, JukeCookieFilter.COOKIE_SESSION_ID);

        if (sessionId == null) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("status", "no_session");
            return ResponseEntity.ok(body);
        }

        Optional<JukeSessionEntry> entryOpt = registry.get(sessionId);
        if (!entryOpt.isPresent()) {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("status", "expired");
            body.put("sessionId", sessionId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        JukeSessionEntry entry = entryOpt.get();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "active");
        body.put("sessionId", entry.getSessionId());
        body.put("track", entry.getTrackName());
        body.put("createdAt", entry.getCreatedAt().toString());

        return ResponseEntity.ok(body);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
