package org.juke.remix.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeStorage;
import org.juke.remix.service.RecordingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 2 — concrete {@link JukeRecordingFinaliser} that wraps the existing
 * {@link RecordingService} so the new {@code JukeStopController} can finalise the in-progress
 * recording, read the bytes from disk, and produce a bundle name + {@link JukeSessionContext}
 * suitable for the Phase 2 packaging step.
 *
 * <p><strong>Track-name handling.</strong> {@code RecordingService.start(track)} discards the
 * track name today and writes to a fixed {@code testRun} ZIP. The Phase 2 controller needs the
 * original track name as the bundle name, so {@link #rememberTrack(String)} is called by the
 * begin-record controller before it kicks off the recording. The finaliser holds the value in
 * an {@link java.util.concurrent.atomic.AtomicReference}-style field; concurrent recordings are
 * not a real concern (the global Juke state is single-tenant during record) but the volatile
 * keyword keeps the contract honest.
 *
 * <p>Profile-gated to {@code record}/{@code replay} so production builds don't instantiate it,
 * matching {@link RecordingService}'s own profile gate.
 */
@Component
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
public class RemixJukeRecordingFinaliser implements JukeRecordingFinaliser {

    private static final Logger LOG = LoggerFactory.getLogger(RemixJukeRecordingFinaliser.class);

    private final RecordingService recordingService;
    private volatile String currentTrackName;
    /** Optional human-readable label for this recording (e.g. "Greetings — Basic Flow Test"). */
    private volatile String currentLabel;

    public RemixJukeRecordingFinaliser(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    /**
     * Stash the track name passed to {@code /service/record/start} so {@link #finalise()} can
     * stamp it into the bundle manifest as the bundle name.
     */
    public void rememberTrack(String trackName) {
        this.currentTrackName = trackName;
    }

    /**
     * Stash the optional label passed as {@code label=…} to {@code /service/record/start}.
     * It is written to {@code juke-metadata.json} inside the recording ZIP so the
     * report endpoint can surface it without requiring a separate sidecar file.
     */
    public void rememberLabel(String label) {
        this.currentLabel = label;
    }

    @Override
    public Result finalise() {
        // Write juke-metadata.json into the ZIP *before* stop() closes it.
        // The DAO's entriesMap is still open at this point; stop() → write()
        // → writeToZipFile() will flush everything including this entry.
        writeMetadata();

        recordingService.stop();

        String zipPath = recordingService.path();
        if (zipPath == null || zipPath.isBlank()) {
            throw new IllegalStateException(
                    "RecordingService.path() returned null/blank — recording was not started");
        }

        byte[] zipBytes;
        try {
            zipBytes = Files.readAllBytes(Path.of(zipPath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read finalised Juke ZIP at " + zipPath, e);
        }

        String bundleName = currentTrackName;
        if (bundleName == null || bundleName.isBlank()) {
            bundleName = "track";
            LOG.warn("No track name was remembered before finalising — falling back to '{}'", bundleName);
        }

        JukeSessionContext ctx = new JukeSessionContext();
        ctx.setSessionId(UUID.randomUUID().toString());
        ctx.setTrackName(bundleName);
        ctx.setPlaybackActive(false);

        currentTrackName = null;
        currentLabel = null;
        LOG.info("Finalised Juke recording '{}' ({} bytes) from {}", bundleName, zipBytes.length, zipPath);
        return new Result(zipBytes, bundleName, ctx);
    }

    /**
     * Public facade called by {@code RemixWebController.endRecord()} before it calls
     * {@code recordingService.stop()}, so the metadata entry is flushed into the ZIP
     * even when the legacy {@code /service/record/end} endpoint is used (rather than
     * the Phase 2 {@code /service/juke/stop} which calls {@link #finalise()}).
     */
    public void flushMetadata() {
        writeMetadata();
    }

    /**
     * Writes a {@code juke-metadata.json} entry into the active recording's ZIP via
     * the global DAO. Must be called <em>before</em> {@code recordingService.stop()}
     * (which calls {@code write()} → {@code writeToZipFile()}).
     */
    private void writeMetadata() {
        try {
            JukeStorage dao = JukeHelper.getJukeDAO();
            if (dao == null) return;

            String track = currentTrackName != null ? currentTrackName.trim() : "track";
            String label = currentLabel != null ? currentLabel.trim() : "";

            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("track", track);
            metadata.put("label", label);
            metadata.put("recordedAt", Instant.now().toString());

            String json = new ObjectMapper().writeValueAsString(metadata);
            dao.writeDirectEntry("juke-metadata.json", json);
            LOG.info("Wrote juke-metadata.json (track='{}', label='{}')", track, label);
        } catch (Exception e) {
            LOG.warn("Failed to write juke-metadata.json: {}", e.getMessage());
        }
    }
}
