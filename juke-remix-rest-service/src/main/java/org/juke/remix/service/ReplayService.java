package org.juke.remix.service;

/**
 * Replay half of the formerly combined {@code RemixService}. Controls the
 * replay lifecycle — start playback of a named track, and toggle the
 * global disable flag so tests can opt out of replay on individual calls.
 *
 * <p>Phase 6 (Single Responsibility): extracted from {@code RemixService} so
 * recording, replay, and tuner concerns can evolve independently.
 */
public interface ReplayService {

    /**
     * Begins replay of the given recorded track. The track name must be on
     * the whitelist in {@code juke.tests}; otherwise a NOK response is
     * returned.
     *
     * @param track logical name of a previously recorded track
     * @return {@link RemixUtil#OK} on success or a NOK variant otherwise
     */
    String start(String track);

    /**
     * Re-enables replay after a prior {@link #disable()} call.
     *
     * @return {@link RemixUtil#OK} on success
     */
    String enable();

    /**
     * Disables replay globally and resets the internal handler cache so a
     * subsequent enable starts fresh.
     *
     * @return {@link RemixUtil#OK} on success
     */
    String disable();
}
