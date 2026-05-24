package org.juke.framework.session;

/**
 * Request-scoped value holder populated once per HTTP request by
 * {@link JukeCookieFilter}.
 * <p>
 * Because this is a {@code @RequestScope} bean, Spring creates a <b>new
 * instance for every HTTP request</b>. Two concurrent requests each get
 * their own {@code JukeSessionContext}, providing natural per-request
 * isolation between Playwright test sessions and regular users.
 * <p>
 * If no Juke cookies are present on the request, all fields remain at
 * their defaults ({@code null} / {@code false}) and
 * {@link #isPlaybackActive()} returns {@code false}, meaning the
 * request is served by the real upstream service.
 */
public class JukeSessionContext {

    private String sessionId;
    private String trackName;
    private boolean playbackActive;

    public JukeSessionContext() {
        // Defaults: no active session
    }

    /**
     * Returns {@code true} if both Juke cookies were present and valid
     * on the current HTTP request.
     */
    public boolean isPlaybackActive() {
        return playbackActive;
    }

    public void setPlaybackActive(boolean playbackActive) {
        this.playbackActive = playbackActive;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTrackName() {
        return trackName;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    @Override
    public String toString() {
        return "JukeSessionContext{" +
                "sessionId='" + sessionId + '\'' +
                ", trackName='" + trackName + '\'' +
                ", playbackActive=" + playbackActive +
                '}';
    }
}
