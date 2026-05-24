package org.juke.remix.service.dto;

import java.util.List;

/**
 * Per-session status row intended to drive one line of a status grid UI.
 *
 * <p>The {@link #getLastCall() lastCall} and {@link #getPercentComplete()
 * percentComplete} fields together answer the operator's two practical
 * questions — "which recording JSON did I just hand back?" and "how far
 * through the recording is this session?" — without requiring callers to
 * walk the per-entry {@code steps} array themselves.
 */
public class SessionStatusDto {

    private final String sessionId;
    private final String track;
    private final String mode;
    private final String startTime;
    private final long playTimeMs;
    private final String playTime;
    private final StepSummaryDto summary;
    private final LastCallDto lastCall;
    private final double percentComplete;
    private final List<StepStatusDto> steps;

    public SessionStatusDto(String sessionId,
                            String track,
                            String mode,
                            String startTime,
                            long playTimeMs,
                            String playTime,
                            StepSummaryDto summary,
                            LastCallDto lastCall,
                            double percentComplete,
                            List<StepStatusDto> steps) {
        this.sessionId = sessionId;
        this.track = track;
        this.mode = mode;
        this.startTime = startTime;
        this.playTimeMs = playTimeMs;
        this.playTime = playTime;
        this.summary = summary;
        this.lastCall = lastCall;
        this.percentComplete = percentComplete;
        this.steps = steps;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTrack() {
        return track;
    }

    public String getMode() {
        return mode;
    }

    public String getStartTime() {
        return startTime;
    }

    public long getPlayTimeMs() {
        return playTimeMs;
    }

    public String getPlayTime() {
        return playTime;
    }

    public StepSummaryDto getSummary() {
        return summary;
    }

    /** Most recent replay call resolved on this session, or {@code null} if none yet. */
    public LastCallDto getLastCall() {
        return lastCall;
    }

    /**
     * Coarse progress across the whole session as a 0–100 percentage. Computed
     * as <em>sum(currentIndex) / sum(totalLength) * 100</em> across every
     * recorded entry — entries that haven't been touched yet contribute 0,
     * fully consumed entries contribute their length. Two-decimal precision.
     */
    public double getPercentComplete() {
        return percentComplete;
    }

    public List<StepStatusDto> getSteps() {
        return steps;
    }
}
