package org.juke.remix.service.dto;

import java.util.List;

/**
 * Top-level payload returned by {@code GET /service/sessions}. Enumerates
 * every currently active Juke replay session with per-entry step progress
 * suitable for rendering in a status grid UI.
 */
public class SessionsStatusResponse {

    private final String generatedAt;
    private final int activeSessionCount;
    private final List<SessionStatusDto> sessions;

    public SessionsStatusResponse(String generatedAt,
                                  int activeSessionCount,
                                  List<SessionStatusDto> sessions) {
        this.generatedAt = generatedAt;
        this.activeSessionCount = activeSessionCount;
        this.sessions = sessions;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public int getActiveSessionCount() {
        return activeSessionCount;
    }

    public List<SessionStatusDto> getSessions() {
        return sessions;
    }
}
