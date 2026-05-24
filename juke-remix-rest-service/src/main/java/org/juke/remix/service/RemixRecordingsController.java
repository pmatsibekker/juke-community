package org.juke.remix.service;

import org.juke.framework.exception.JukeStorageException;
import org.juke.framework.storage.JukeStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 6 prerequisite — exposes the agent's recording store over HTTP so
 * the admin server's {@code POST /admin-api/recordings/import} pull can
 * shuttle ZIP blobs across the wire and index them centrally.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /service/recordings} — list every recording the agent
 *       currently holds, by name.</li>
 *   <li>{@code GET /service/recordings/{name}} — return the named
 *       recording's ZIP bytes; {@code 404} if the recording is unknown.</li>
 * </ul>
 *
 * <p>Both endpoints delegate straight to {@link JukeStorage}'s recording-
 * store API ({@link JukeStorage#listRecordings()},
 * {@link JukeStorage#loadRecording(String)}) introduced in Phase 5.A.
 * The bound implementation is determined by the host's auto-config —
 * folder-backed by default, JPA-backed when {@code juke.storage.backend=db}
 * — so the agent works uniformly regardless of which backend an Enterprise
 * customer wires in.
 *
 * <p>Always-on (no {@code @Profile} gate). The admin server pulls
 * recordings even from agents that are not actively recording or
 * replaying; the underlying {@link JukeStorage} is registered
 * unconditionally.
 */
@RestController
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
@RequestMapping("/service/recordings")
public class RemixRecordingsController {

    private static final Logger LOG = LoggerFactory.getLogger(RemixRecordingsController.class);

    private final JukeStorage storage;

    public RemixRecordingsController(JukeStorage storage) {
        this.storage = storage;
    }

    @GetMapping
    public List<String> list() {
        return storage.listRecordings();
    }

    // {name:.+} captures the full path segment including dots so a
    // recording named e.g. "checkout.flow.v2" survives intact rather
    // than being truncated at the first dot by Spring's default
    // path-variable parsing.
    @GetMapping(value = "/{name:.+}", produces = "application/zip")
    public ResponseEntity<byte[]> load(@PathVariable String name) {
        try {
            byte[] bytes = storage.loadRecording(name);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + name + ".zip\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(bytes);
        } catch (JukeStorageException e) {
            // The recording-store contract surfaces "no such recording" as
            // JukeStorageException — translate to a clean 404 for the wire.
            LOG.debug("Recording lookup miss: {} ({})", name, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
