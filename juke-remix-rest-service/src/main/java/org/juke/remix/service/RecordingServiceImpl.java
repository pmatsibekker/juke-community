package org.juke.remix.service;

import org.juke.framework.config.ConfigUtil;
import org.juke.framework.proxy.JukeFactory;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link RecordingService}. Flips the global Juke
 * state to {@link JukeState#RECORD} on {@link #start(String)} and installs a
 * fresh ZIP-backed DAO keyed by the track name. {@link #stop()} reverts to
 * {@link JukeState#NONE} and asks the DAO to flush.
 *
 * <p>Profile-gated to {@code record} and {@code replay} so production builds
 * don't instantiate the recording stack.
 */
@Service
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
public class RecordingServiceImpl implements RecordingService {

    /** Fallback ZIP name when neither the track parameter nor {@code juke.zip} is set. */
    public static final String TESTRUN = "testRun";

    @Override
    public String start(String track) {
        // KI-3 fix: previously set state to JukeState.JUKE which is a parameter
        // sentinel, not a real mode — JukeMode.fromLegacyString("juke") falls
        // through to NONE, so /service/record/end then short-circuits with
        // "Unavailable Service" because JukeState.getGlobaljuke() returns null.
        // The recording service must put the runtime in RECORD mode (parity
        // with ReplayServiceImpl, which correctly uses JukeState.REPLAY).
        JukeFactory.setGlobaljuke(JukeState.RECORD);

        // KI-3 fix: honour the track URL parameter so the bundle name matches
        // what the caller asked for. Falls back to the configured juke.zip,
        // then to the historic TESTRUN default for callers that pass nothing.
        String zipName = pickZipName(track);
        JukeHelper.setJukeDao(new JukeZipDAOImpl(ConfigUtil.getJukePath(), zipName));
        return RemixUtil.OK;
    }

    @Override
    public String stop() {
        // Revert to IGNORE (passthrough), not NONE. Functionally both stop
        // recording, but JukeState.getGlobaljuke() maps NONE -> null, which the
        // /service/* controllers treat as "Unavailable Service" and reject with
        // HTTP 500. Leaving NONE here bricked the whole control surface after a
        // normal record/end — you couldn't start a replay, schedule a remix, or
        // even re-record without restarting the JVM. IGNORE reports a non-null
        // mode, so record -> replay works in a single process.
        JukeFactory.setGlobaljuke(JukeState.IGNORE);
        JukeHelper.getJukeDAO().write();
        return RemixUtil.OK;
    }

    @Override
    public String path() {
        return JukeHelper.getJukeDAO().path();
    }

    private static String pickZipName(String track) {
        if (track != null && !track.isBlank()) return track;
        String configured = ConfigUtil.getJukeZip();
        if (configured != null && !configured.isBlank()) return configured;
        return TESTRUN;
    }
}
