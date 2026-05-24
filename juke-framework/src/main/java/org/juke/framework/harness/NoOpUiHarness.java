package org.juke.framework.harness;

import org.juke.framework.session.JukeSessionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default {@link UiHarness} — produces no artefact, executes no UI step. Selected when
 * {@code juke.ui-harness=none} (or unset). With this harness active, Juke runs fully headless:
 * recordings stop with a plain Juke ZIP and replay is driven entirely through the in-process
 * {@code HeadlessJukeRunner} (Phase 4.5) via cookie-bound REST calls to the application's
 * controllers.
 *
 * <p>This bean is registered unconditionally so the application starts even when no other
 * harness is on the classpath. The actual {@code @Primary} election happens in
 * {@code JukeHarnessConfiguration} based on the {@code juke.ui-harness} property.
 */
@Component
public class NoOpUiHarness implements UiHarness {

    public static final String ID = "none";

    private static final Logger LOG = LoggerFactory.getLogger(NoOpUiHarness.class);

    private static final UiArtefactDescriptor DESCRIPTOR =
            new UiArtefactDescriptor("none.zip", "application/zip", "No UI harness");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public UiArtefactDescriptor describeArtefact() {
        return DESCRIPTOR;
    }

    @Override
    public void onSessionStart(JukeSessionContext ctx) {
        LOG.debug("NoOpUiHarness onSessionStart for session={}, track={}",
                ctx.getSessionId(), ctx.getTrackName());
    }

    @Override
    public Optional<byte[]> onSessionStop(JukeSessionContext ctx) {
        LOG.debug("NoOpUiHarness onSessionStop for session={} — no artefact produced",
                ctx.getSessionId());
        return Optional.empty();
    }

    @Override
    public boolean canExecuteRecording() {
        return false;
    }

    @Override
    public void executeUseCase(UseCaseExecutionPlan plan) {
        // No-op: the HeadlessJukeRunner (Phase 4.5) consumes runs targeting Juke-only flows.
        LOG.debug("NoOpUiHarness executeUseCase({}) — handed off to HeadlessJukeRunner",
                plan.useCaseExecutionId());
    }
}
