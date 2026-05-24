package org.juke.framework.proxy;

import java.util.Map;

import org.juke.framework.config.JukeSpringContextHolder;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

public class JukeFactory<T> {
	private static final Logger LOG = LoggerFactory.getLogger(JukeFactory.class);

	static {
		final String mode = ConfigUtil.getJukeMode();
		if (!JukeState.IGNORE.equals(JukeState.getGlobaljuke())) {
			// Route through the setter so JukeRuntimeHolder mirrors the change.
			JukeState.setGlobaljuke((mode == null || !mode.equalsIgnoreCase(JukeState.RECORD)
				&& !mode.equalsIgnoreCase(JukeState.REPLAY)) ? JukeState.IGNORE : mode);
		}
			try {
				JukeHelper.setJukeDao(new JukeZipDAOImpl(ConfigUtil.getJukePath(), ConfigUtil.getJukeZip()));
			} catch (JukeAccessException e) {
				LOG.error("Failed to initialize Juke DAO for path={}, zip={}: {}",
						ConfigUtil.getJukePath(), ConfigUtil.getJukeZip(), e.getMessage(), e);
			}


	}

	/**
	 * Proxy cache keyed by interface class.
	 * <p>
	 * Phase 3 Step F — the {@code static} map was moved to
	 * {@link JukeRuntimeHolder#current()}{@code .proxyCache()} so that parallel
	 * sessions can own independent caches. The accessor preserves the old
	 * semantics for callers still reaching in.
	 */
	private static Map<Class<?>, Object> jukeMockCache() {
		return JukeRuntimeHolder.current().proxyCache();
	}

	public static void setGlobaljuke(String global) {
		// Route through JukeState so the Phase-3 runtime holder stays in sync.
		JukeState.setGlobaljuke(global);
	}
	public static void resetReplay() {
		ReplayHandler.resetReplay();
	}

	public static String resolveJukeState(String juke) {
		if (juke==null) return null;

		// Phase 3 Step C — resolve the global mode through the runtime holder
		// instead of reading the legacy static directly. The holder is mirrored
		// from the static by every setter, so semantics are unchanged.
		String globalMode = JukeRuntimeHolder.current().mode().legacyString();
		boolean hasGlobal = globalMode != null && !globalMode.isEmpty();

		if (hasGlobal && ( globalMode.equalsIgnoreCase(JukeState.RECORD)
				|| globalMode.equalsIgnoreCase(JukeState.REPLAY)) &&
				(!JukeState.NONE.equalsIgnoreCase(juke))) {
			return globalMode;

		} else if (hasGlobal && globalMode.equalsIgnoreCase(JukeState.IGNORE)) {
			return JukeState.NONE;
		} else {
			return juke;
		}
	}

	public synchronized T newInstance(T wrapped, Class<T> clazz, String jukeInput) {

		// --- NEW: check for active per-session playback context ---
		// If a valid Juke session cookie is present on the current HTTP request,
		// route to a per-session SessionAwareReplayHandler instead of the global
		// replay logic. This enables concurrent Playwright test sessions.
		try {
			ApplicationContext appCtx = JukeSpringContextHolder.get();
			if (appCtx != null) {
				JukeSessionContext sessionCtx = appCtx.getBean(JukeSessionContext.class);
				if (sessionCtx.isPlaybackActive()) {
					LOG.debug("Session-aware replay active for {} (session {})",
							clazz.getSimpleName(), sessionCtx.getSessionId());
					SessionRegistry registry = appCtx.getBean(SessionRegistry.class);
					return SessionAwareReplayHandler.newProxy(
							wrapped, clazz, sessionCtx, registry);
				}
			}
		} catch (Exception e) {
			// Not in a web request context (e.g., startup, background thread,
			// or running outside Spring) — fall through to existing logic.
			LOG.trace("No active Juke session context, using global state: {}", e.getMessage());
		}
		// --- END session-aware check ---

		try {
			String juke = resolveJukeState(jukeInput);
			if (juke != null) {
				if (juke.equalsIgnoreCase(JukeState.REPLAY)) {
					T result = null;
					Map<Class<?>, Object> cache = jukeMockCache();
					Object cached = cache.get(clazz);
					if (cached != null) {
						// Cache is erased to Object at the package boundary
						// (see JukeRuntime.proxyCache). Entries were inserted
						// under the same {@code clazz} key, so the cast is
						// safe by construction.
						@SuppressWarnings("unchecked")
						T typed = (T) cached;
						result = typed;
					} else {
						// ReplayHandler.getReplayHandlerCache() returns the
						// erased shared cache; entries keyed by {@code clazz}
						// always hold {@code ReplayHandler<T>} by construction.
						@SuppressWarnings("unchecked")
						ReplayHandler<T> handler =
								(ReplayHandler<T>) ReplayHandler.getReplayHandlerCache().get(clazz);
						if (handler == null)
							handler = new ReplayHandler<T>(wrapped, clazz);
						result = handler.newInstance(clazz);
					}
					cache.put(clazz, result);
					return result == null ? wrapped : result;

				} else if (juke.equalsIgnoreCase(JukeState.RECORD)) {
					return new RecordHandler<T>().newInstance(wrapped, clazz);
				} else {
					return wrapped;
				}
			} else {
				return wrapped;
			}

		} catch (Exception yae) {
			LOG.error("can not find juke at " + ConfigUtil.getJukePath() + "/" + ConfigUtil.getJukeZip());
			return wrapped;
		}
	}
}
