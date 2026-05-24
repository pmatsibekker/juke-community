package org.juke.remix.service;

import org.juke.framework.metadata.DataProgram;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.metadata.JukeStateBuilder;
import org.juke.framework.proxy.JukeNameFormatter;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.juke.remix.service.dto.LastCallDto;
import org.juke.remix.service.dto.SessionStatusDto;
import org.juke.remix.service.dto.SessionsStatusResponse;
import org.juke.remix.service.dto.StepStatusDto;
import org.juke.remix.service.dto.StepSummaryDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller exposing an aggregate, read-only view of every active
 * Juke replay session currently tracked in the {@link JukeSessionRegistry}.
 * <p>
 * Intended to feed a status grid UI: for each session it reports the track
 * being replayed, start time, current total play time, and per-entry step
 * progress (completed / in progress / not started) plus roll-up counts.
 */
@RestController
@RequestMapping("/service/sessions")
@ConditionalOnProperty(name = "juke.enabled", havingValue = "true")
public class RemixSessionsController {

    private static final Logger log = LoggerFactory.getLogger(RemixSessionsController.class);

    @Autowired
    private SessionRegistry registry;

    @GetMapping
    public ResponseEntity<SessionsStatusResponse> listSessions() {
        Instant now = Instant.now();
        Collection<JukeSessionEntry> snapshot = registry.snapshot();

        List<SessionStatusDto> sessions = new ArrayList<>(snapshot.size());
        for (JukeSessionEntry entry : snapshot) {
            sessions.add(toDto(entry, now));
        }

        SessionsStatusResponse body = new SessionsStatusResponse(
                now.toString(),
                sessions.size(),
                sessions);
        return ResponseEntity.ok(body);
    }

    private SessionStatusDto toDto(JukeSessionEntry entry, Instant now) {
        List<StepStatusDto> steps = buildSteps(entry);
        StepSummaryDto summary = summarise(steps);
        Duration playTime = Duration.between(entry.getCreatedAt(), now);
        LastCallDto lastCall = buildLastCall(entry);
        double percentComplete = computePercentComplete(steps);
        return new SessionStatusDto(
                entry.getSessionId(),
                entry.getTrackName(),
                "replay",
                entry.getCreatedAt().toString(),
                Math.max(playTime.toMillis(), 0L),
                playTime.toString(),
                summary,
                lastCall,
                percentComplete,
                steps);
    }

    /**
     * Builds the {@link LastCallDto} from the session's most-recent
     * {@code sequencedKey} (set by the replay handler). Returns {@code null}
     * if the session has not resolved a single call yet.
     */
    private static LastCallDto buildLastCall(JukeSessionEntry entry) {
        String sequencedKey = entry.getLastCalledKey();
        Instant at = entry.getLastCalledAt();
        if (sequencedKey == null) return null;

        // Split the sequenced key into <entry>.<seq>. Defensive against
        // malformed keys (sequence not parseable as int).
        String entryKey = sequencedKey;
        int sequence = 0;
        int lastDot = sequencedKey.lastIndexOf('.');
        if (lastDot > 0 && lastDot < sequencedKey.length() - 1) {
            String tail = sequencedKey.substring(lastDot + 1);
            try {
                sequence = Integer.parseInt(tail);
                entryKey = sequencedKey.substring(0, lastDot);
            } catch (NumberFormatException ignore) {
                // Trailing segment isn't an int — keep the whole key as entry
                // and leave sequence at 0.
            }
        }

        String displayName = toDisplayName(entryKey, sequence);
        return new LastCallDto(entryKey, displayName, sequence,
                at != null ? at.toString() : null);
    }

    /**
     * Renders an entry key as {@code SimpleType.method[.sequence]} for the
     * status UI. Strips the package prefix and JukeParser's leading {@code $}
     * marker.
     */
    private static String toDisplayName(String entryKey, int sequence) {
        int splitAt = entryKey.lastIndexOf('.');
        String typePart;
        String methodPart;
        if (splitAt > 0 && splitAt < entryKey.length() - 1) {
            typePart = entryKey.substring(0, splitAt);
            methodPart = entryKey.substring(splitAt + 1);
        } else {
            typePart = entryKey;
            methodPart = "";
        }
        String simpleType = JukeNameFormatter.simpleName(typePart);
        String cleanMethod = methodPart.startsWith("$") ? methodPart.substring(1) : methodPart;
        StringBuilder sb = new StringBuilder(simpleType);
        if (!cleanMethod.isEmpty()) {
            sb.append('.').append(cleanMethod);
        }
        if (sequence > 0) {
            sb.append('.').append(sequence);
        }
        return sb.toString();
    }

    /**
     * Coarse progress across the whole session as a 0–100 percentage. Sum of
     * each entry's progress index over the sum of every entry's total length.
     * Returns 0.0 when the session has nothing to replay (empty universe).
     * Rounded to two decimal places to keep the JSON payload tidy.
     */
    private static double computePercentComplete(List<StepStatusDto> steps) {
        long progress = 0L;
        long total = 0L;
        for (StepStatusDto step : steps) {
            progress += Math.max(0, step.getCurrentIndex());
            total += Math.max(0, step.getTotalLength());
        }
        if (total <= 0L) return 0.0;
        double pct = (100.0 * progress) / total;
        return Math.round(pct * 100.0) / 100.0;
    }

    /**
     * Builds the per-entry step progress list for one session.
     * <p>
     * The "universe" of entries is derived from the session's ZIP filenames
     * via {@link JukeStateBuilder} (seeded schedule with {@code totalLength}
     * set, {@code index == 0}). Actual progress is then overlaid from every
     * per-interface schedule the session has materialised so far, taking the
     * maximum {@code index} per entry in the unlikely case of overlap.
     */
    private List<StepStatusDto> buildSteps(JukeSessionEntry entry) {
        Set<String> fileNames;
        try {
            fileNames = entry.getDao().getFileNames();
        } catch (RuntimeException e) {
            log.warn("Failed to enumerate ZIP entries for session {} (track '{}'): {}",
                    entry.getSessionId(), entry.getTrackName(), e.getMessage());
            return List.of();
        }

        DataProgramSchedule seeded = new JukeStateBuilder.Builder(fileNames).build().getSchedule();
        Map<String, DataProgram> universe = seeded.snapshotPrograms();

        // Build a progress map containing only entries that were explicitly advanced
        // beyond their initial seeded state.
        //
        // DataProgram.setLength() auto-initialises every entry's index to 1 so that
        // the 1-based replay cursor works correctly.  That means even entries that
        // were NEVER accessed by a real replay call start at index=1 in the session
        // schedule – indistinguishable from "called once" without this comparison.
        //
        // By cross-referencing with the freshly-seeded universe (which has the same
        // initial index), we can tell the two cases apart:
        //   sessionIdx  > seededIdx  → entry was explicitly advanced (record progress)
        //   sessionIdx == seededIdx  → entry was never advanced (treat as 0 / not-started)
        Map<String, Integer> progress = new LinkedHashMap<>();
        for (DataProgramSchedule schedule : entry.getSchedules()) {
            for (Map.Entry<String, DataProgram> e : schedule.snapshotPrograms().entrySet()) {
                int sessionIdx = e.getValue().getIndex();
                DataProgram seededDp = universe.get(e.getKey());
                int initIdx = (seededDp != null) ? seededDp.getIndex() : 1;
                if (sessionIdx > initIdx) {
                    progress.merge(e.getKey(), sessionIdx, Math::max);
                }
            }
        }

        List<StepStatusDto> steps = new ArrayList<>(universe.size());
        for (Map.Entry<String, DataProgram> e : universe.entrySet()) {
            String key = e.getKey();
            int totalLength = e.getValue().getLength();
            int currentIndex = progress.getOrDefault(key, 0);
            steps.add(new StepStatusDto(key, currentIndex, totalLength, classify(currentIndex, totalLength)));
        }
        return steps;
    }

    private static StepStatusDto.Status classify(int currentIndex, int totalLength) {
        if (currentIndex <= 0) {
            return StepStatusDto.Status.NOT_STARTED;
        }
        if (totalLength > 0 && currentIndex >= totalLength) {
            return StepStatusDto.Status.COMPLETED;
        }
        return StepStatusDto.Status.IN_PROGRESS;
    }

    private static StepSummaryDto summarise(List<StepStatusDto> steps) {
        int completed = 0;
        int inProgress = 0;
        int notStarted = 0;
        for (StepStatusDto step : steps) {
            switch (step.getStatus()) {
                case "completed":
                    completed++;
                    break;
                case "in_progress":
                    inProgress++;
                    break;
                default:
                    notStarted++;
                    break;
            }
        }
        return new StepSummaryDto(steps.size(), completed, inProgress, notStarted);
    }
}
