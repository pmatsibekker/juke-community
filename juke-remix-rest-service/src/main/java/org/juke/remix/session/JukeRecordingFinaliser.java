package org.juke.remix.session;

import org.juke.framework.session.JukeSessionContext;

/**
 * Abstracts the pre-Phase-2 logic that finalises an in-progress Juke recording (flushing the
 * {@code RecordHandler}, closing the on-disk ZIP, reading bytes back) so the new
 * {@code JukeStopController} stays focused on Phase 2 concerns (UI harness coordination,
 * bundle packaging, response shaping).
 *
 * <p><strong>Wiring guidance:</strong> the existing {@code /service/juke/stop} implementation
 * in your codebase already does this work today. Extract that logic into a class that
 * implements this interface and register it as a Spring bean. The new
 * {@code JukeStopController} replaces the existing controller method.
 */
public interface JukeRecordingFinaliser {

    /**
     * Finalise the currently-active recording session. Implementations must:
     * <ul>
     *   <li>Quiesce the recording capture (so no further entries are appended).</li>
     *   <li>Close the on-disk ZIP file the {@code RecordHandler} has been writing to.</li>
     *   <li>Read the ZIP into a byte array suitable for return.</li>
     *   <li>Resolve the {@link JukeSessionContext} for the session that just ended (so the
     *       {@code UiHarness} can correlate its own captured artefact).</li>
     *   <li>Resolve the bundle name (the value originally passed to {@code /service/juke/start}
     *       as {@code testRun}, which becomes the bundle name written into the manifest).</li>
     * </ul>
     */
    Result finalise();

    /** Carries everything the {@code JukeStopController} needs to assemble the response. */
    final class Result {
        private final byte[] jukeZipBytes;
        private final String bundleName;
        private final JukeSessionContext sessionContext;

        public Result(byte[] jukeZipBytes, String bundleName, JukeSessionContext sessionContext) {
            this.jukeZipBytes = jukeZipBytes;
            this.bundleName = bundleName;
            this.sessionContext = sessionContext;
        }

        public byte[] jukeZipBytes() { return jukeZipBytes; }
        public String bundleName() { return bundleName; }
        public JukeSessionContext sessionContext() { return sessionContext; }
    }
}
