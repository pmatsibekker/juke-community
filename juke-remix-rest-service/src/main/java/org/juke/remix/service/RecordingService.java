package org.juke.remix.service;

/**
 * Recording half of the formerly combined {@code RemixService}. Controls the
 * record lifecycle — begin a named track, finalize and persist it, and
 * expose the resulting artifact path for download.
 *
 * <p>Phase 6 (Single Responsibility): extracted from {@code RemixService} so
 * recording, replay, and tuner concerns can evolve independently.
 */
public interface RecordingService {

    /**
     * Begins a new recording session for the given track name. Subsequent
     * proxied calls will be captured until {@link #stop()} is invoked.
     *
     * @param track logical name for the recording
     * @return {@link RemixUtil#OK} on success
     */
    String start(String track);

    /**
     * Finalizes the current recording and flushes it to the configured
     * storage (typically a ZIP on the Juke path).
     *
     * @return {@link RemixUtil#OK} on success
     */
    String stop();

    /**
     * Returns the filesystem path of the current recording artifact, used
     * by the controller to stream the ZIP back to the client.
     */
    String path();
}
