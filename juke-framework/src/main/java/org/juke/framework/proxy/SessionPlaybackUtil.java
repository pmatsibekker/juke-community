package org.juke.framework.proxy;

import org.juke.framework.config.JukeSpringContextHolder;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.Optional;

/**
 * Shared helpers for resolving active cookie-scoped replay sessions.
 */
final class SessionPlaybackUtil {

    private static final Logger LOG = LoggerFactory.getLogger(SessionPlaybackUtil.class);

    private SessionPlaybackUtil() {
    }

    static boolean isGloballyDisabled() {
        String globalMode = JukeRuntimeHolder.current().mode().legacyString();
        return globalMode != null && JukeState.DISABLE.equalsIgnoreCase(globalMode);
    }

    static Optional<JukeSessionEntry> resolveActiveSessionEntry() {
        try {
            ApplicationContext appCtx = JukeSpringContextHolder.get();
            if (appCtx == null) {
                return Optional.empty();
            }
            JukeSessionContext sessionCtx = appCtx.getBean(JukeSessionContext.class);
            if (!sessionCtx.isPlaybackActive()) {
                return Optional.empty();
            }
            SessionRegistry registry = appCtx.getBean(SessionRegistry.class);
            return registry.get(sessionCtx.getSessionId());
        } catch (Exception e) {
            LOG.trace("No active Juke session context: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

