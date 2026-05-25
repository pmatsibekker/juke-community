package org.juke.framework.proxy;

import org.juke.framework.config.ConfigUtil;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.storage.JukeStorage;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.exception.JukeReplayException;
import org.juke.framework.exception.TunerGeneratedException;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.metadata.JukeParser;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.metadata.JukeStateBuilder;
import org.juke.framework.storage.InputArgsRecord;
import org.juke.framework.tuner.JukeCommandProcessorChain;
import org.juke.framework.tuner.ProcessObject;
import org.juke.framework.validation.ReplayValidationHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
public class ReplayHandler<T> extends BaseHandler<T> {
	private static final Logger LOG = LoggerFactory.getLogger(ReplayHandler.class);
	
	private T proxy;
	private T originalService=null;

	/**
	 * Per-runtime cache of {@link ReplayHandler} instances keyed by interface
	 * class. Phase 3 Step F — storage was moved to
	 * {@link JukeRuntimeHolder#current()}{@code .replayHandlerCache()} so that
	 * parallel sessions can own independent caches. The views below adapt the
	 * erased {@code Map<Class<?>, Object>} to the old typed signature.
	 */
	private static Map<Class<?>, ReplayHandler<?>> cacheView() {
		@SuppressWarnings({"unchecked", "rawtypes"})
		Map<Class<?>, ReplayHandler<?>> view =
				(Map) JukeRuntimeHolder.current().replayHandlerCache();
		return view;
	}

	DataProgramSchedule schedule;

	/** ReentrantLock replaces synchronized to avoid pinning Java 21 virtual threads. */
	private final ReentrantLock lock = new ReentrantLock();

	
		
	static {
		// Initialize the global mode at class load ONLY if nothing has set it
		// yet. In an app that uses only concrete-field @Juke (no interface @Juke),
		// ReplayHandler is not loaded until the first resetReplay() — which runs
		// *inside* /service/replay/start, right AFTER it sets REPLAY. Without this
		// guard the block would run then and reset the mode to IGNORE, silently
		// breaking replay for the concrete (RestTemplate) path. (getGlobaljuke()
		// is null only when nothing has set a mode; an active record/replay/ignore
		// mode is preserved.)
		if (JukeState.getGlobaljuke() == null) {
			final String mode = ConfigUtil.getJukeMode();
			// Route through the setter so JukeRuntimeHolder mirrors the change.
			JukeState.setGlobaljuke((mode == null || !mode.equalsIgnoreCase(JukeState.RECORD)
					&& !mode.equalsIgnoreCase(JukeState.REPLAY)) ? JukeState.IGNORE : mode);
		}

		// Same guard for the DAO: only install the configured default if no
		// storage is active yet. Otherwise a lazy ReplayHandler load during
		// /service/replay/start (which has already installed the track DAO) would
		// overwrite it with the configured juke.zip, so replay reads the wrong
		// recording (NoSuchFile) for the concrete path.
		if (JukeRuntimeHolder.current().storage() == null) {
			try {
				JukeHelper.setJukeDao(new JukeZipDAOImpl(ConfigUtil.getJukePath(), ConfigUtil.getJukeZip()));
			} catch (JukeAccessException e) {
				LOG.error("Failed to initialize Juke DAO in ReplayHandler static init for path={}, zip={}: {}",
						ConfigUtil.getJukePath(), ConfigUtil.getJukeZip(), e.getMessage(), e);
			}
		}
	}
	
	public ReplayHandler(T service, Class<T> clazz) {
		Map<Class<?>, ReplayHandler<?>> cache = cacheView();
		if (!cache.containsKey(clazz)) {
			cache.put(clazz, this);
		}
		this.originalService = service;
		this.setInterfaceClass(clazz);
		this.setup();
	}
	
	
	
	
	

	public static void resetDelays() {
	//	track = new JukTrack();
	}
	public T getProxy() {
		return proxy;
	}

	public void setProxy(T proxy) {
		this.proxy = proxy;
	}

	public T getOriginalService() {
		return originalService;
	}

	public void setOriginalService(T originalService) {
		this.originalService = originalService;
	}
	
	public static void resetReplay() {
		for (ReplayHandler<?> handler : cacheView().values()) {
			handler.reset();
		}
	}
	public static Map<Class<?>, ReplayHandler<?>> getReplayHandlerCache(){
		return cacheView();
	}
	public void reset() {
		lock.lock();
		try {
			this.setInitialized(false);
			ReplayHandler<?> handler = getReplayHandlerCache().get(this.getInterfaceClass());
			if (handler != null)
				handler.setInitialized(false);
			resetDelays();
		} finally {
			lock.unlock();
		}
	}


	public static Class<?> getMockClass(Class<?> instanceClass, Class<?> interfaceClass) {
		if (Arrays.asList(instanceClass.getInterfaces()).contains(interfaceClass))
			return instanceClass;
		else if (instanceClass.equals(Object.class))
			return instanceClass;
		else
			return getMockClass(instanceClass.getSuperclass(), interfaceClass);
	}
	
	public void setup() {
		lock.lock();
		try {
		
		if (this.isInitialized())
			return;
		
		// Only create a new DAO from config if one has not already been set
		// (e.g. by beginReplay switching to a different track).
		// Phase 3 Step C — read through the runtime holder.
		if (JukeRuntimeHolder.current().storage() == null) {
			JukeHelper.setJukeDao(new JukeZipDAOImpl(ConfigUtil.getJukePath(), ConfigUtil.getJukeZip()));
		}

		JukeStorage dao = JukeRuntimeHolder.current().storage();
		LOG.info("setup() using DAO path: {}", dao.path());

		//String JukeClassTxt=JukeHelper.getJukeDAO().asString( "juke");
		Class<?> clazz = originalService.getClass();

		//builds juke class form juke.json

		if (!JukeClass.instance().containsKey(this.getInterfaceClass().getCanonicalName())) {
			JukeClass JukeClass = JukeConfigBuilder.set(this.getInterfaceClass()).build();
			JukeClass.instance().put(clazz.getCanonicalName(), JukeClass);
			setJukeClass(JukeClass);
		}
		//set schedule
		Set<String> fileNames = dao.getFileNames();
		LOG.info("setup() building schedule from {} entries: {}", fileNames.size(), fileNames);
		JukeStateBuilder built= new JukeStateBuilder.Builder(fileNames).build();
		this.schedule = built.getSchedule();
		this.setInitialized(true);
		} finally {
			lock.unlock();
		}
	}
	
	

	
	public T newInstance(Class<T> clazz) {
		Map<Class<?>, ReplayHandler<?>> cache = cacheView();
		if (!cache.containsKey(clazz)) {
			cache.put(clazz, this);
		}
		return JukeProxyFactory.createInterfaceProxy(clazz, cache.get(clazz));
	}
	
	
	
	@Override
	public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
		lock.lock();
		MdcToken mdcToken = pushInvocationMdc(m);
		try {
			// Object-inherited methods (toString/hashCode/equals) are never replayed.
			if (!JukeMethodFilter.shouldIntercept(m)) {
				return respondToObjectMethod(proxy, m, args);
			}

			setup();

			Class<?> targetClass = this.getInterfaceClass();
			List<Method> foundMethods = JukeParser.isInMethods(m.getName(), targetClass.getMethods());
			String parsedMethodName = JukeParser.getMethodName(targetClass, m);
			String callerFullName = targetClass.getName() + ".$" + parsedMethodName;
			String typeDiscriminator = TypeDiscriminatorUtil.extractTypeDiscriminator(m, args);
			Class<?> runtimeType = TypeDiscriminatorUtil.extractTypeDiscriminatorClass(m, args);

			Object result = null;
			TunerGeneratedException tge = null;
			try {
				if (foundMethods != null && !foundMethods.isEmpty()) {
					LOG.debug(m.getName() + " needs special handling by juke due to overloading");
				}
				for (Method candidate : foundMethods) {
					if (!JukeParser.isAssignableFromArguments(candidate, args)) continue;
					if (!callerFullName.equalsIgnoreCase(
							targetClass.getName() + ".$" + JukeParser.getMethodName(targetClass, m))) continue;

					try {
						String sequencedFullName = resolveSequencedName(
								targetClass, parsedMethodName, callerFullName, typeDiscriminator);

						JukeStorage invokeDao = JukeRuntimeHolder.current().storage();
						LOG.info("invoke() reading entry '{}' from DAO path: {}",
								sequencedFullName, invokeDao.path());
						String json = invokeDao.asString(sequencedFullName);

						result = deserialize(invokeDao, targetClass, sequencedFullName, runtimeType);

						JukeHelper.validateInputArgs(sequencedFullName, candidate, args);

						// Phase 3 §5.3 — per-field input diff against the .args.json sidecar,
						// honouring FieldIgnoreRule entries from the scenario service.
						// No-op unless a ReplayContext + IgnoreRuleProvider are wired in.
						InputArgsRecord recordedArgs = JukeHelper.readInputArgs(sequencedFullName);
						ReplayValidationHooks.runInputDiff(
								targetClass, candidate, args,
								recordedArgs != null ? recordedArgs.getArguments() : null);

						runTunerChain(sequencedFullName, json, result);
						break;
					} catch (TunerGeneratedException tgexception) {
						tge = tgexception;
					} catch (Exception e) {
						LOG.info("Exception found" + e.getClass().getSimpleName() + "->" + e.getMessage());
					}
				}
				LOG.debug("Completed extracting result from file " + m.getName());
			} catch (Exception e) {
				throw new JukeReplayException("Unexpected invocation exception during replay of "
						+ this.getInterfaceClass().getName() + "." + m.getName() + ": " + e.getMessage(), e);
			} finally {
				LOG.info("after method " + m.getName());
			}
			if (tge != null) {
				// Phase 3 §5.3 — surface tuner-generated throwables as captured exceptions
				// so the scenario layer marks the use case FAILED with EXCEPTION.
				throw ReplayValidationHooks.captureException(targetClass, m, tge.getWrappedException());
			}
			return result;
		} finally {
			popInvocationMdc(mdcToken);
			lock.unlock();
		}
	}

	/**
	 * Canonical responses for the three {@link Object} methods the JDK always
	 * forwards through a proxy. These must never be replayed from the DAO.
	 */
	private Object respondToObjectMethod(Object proxy, Method m, Object[] args) {
		String name = m.getName();
		if ("toString".equals(name)) return "JukeReplayProxy[" + this.getInterfaceClass().getSimpleName() + "]";
		if ("hashCode".equals(name)) return System.identityHashCode(proxy);
		if ("equals".equals(name))   return proxy == args[0];
		return null;
	}

	/**
	 * Resolves the next available recorded entry for the given call. Tries four
	 * naming schemes in order of preference, so older recorded sessions remain
	 * replayable after the identifier format has evolved:
	 *
	 * <ol>
	 *   <li>Short discriminated (new default when a {@code Class<?>} arg is present)</li>
	 *   <li>Short plain (new default otherwise)</li>
	 *   <li>Legacy full-path discriminated</li>
	 *   <li>Legacy full-path plain</li>
	 * </ol>
	 */
	private String resolveSequencedName(Class<?> targetClass, String parsedMethodName,
			String callerFullName, String typeDiscriminator) {
		String shortDiscriminated = JukeNameFormatter.buildShortIdentifier(
				targetClass, parsedMethodName, typeDiscriminator);
		if (typeDiscriminator != null && this.schedule.size(shortDiscriminated) > 0) {
			return this.schedule.getNextAvailable(shortDiscriminated);
		}
		String shortPlain = JukeNameFormatter.buildShortIdentifier(
				targetClass, parsedMethodName, null);
		if (this.schedule.size(shortPlain) > 0) {
			return this.schedule.getNextAvailable(shortPlain);
		}
		String legacyDiscriminated = TypeDiscriminatorUtil.buildRecordIdentifier(
				callerFullName, typeDiscriminator);
		if (typeDiscriminator != null && this.schedule.size(legacyDiscriminated) > 0) {
			return this.schedule.getNextAvailable(legacyDiscriminated);
		}
		return this.schedule.getNextAvailable(callerFullName);
	}

	/**
	 * Type-aware deserialization: when the caller supplied a {@code Class<?>}
	 * discriminator argument, ask the DAO to deserialize as that runtime type;
	 * otherwise defer to the method's declared return type.
	 */
	private Object deserialize(JukeStorage dao, Class<?> targetClass,
			String sequencedFullName, Class<?> runtimeType) {
		if (runtimeType != null && runtimeType != Object.class) {
			return dao.readFromFileAsType(targetClass, sequencedFullName, runtimeType);
		}
		return dao.readFromFile(targetClass, sequencedFullName);
	}

	/**
	 * Feeds the deserialized replay payload through the tuner command chain so
	 * registered tuners (delays, failures, field mutators) can act on it before
	 * it is returned to the caller.
	 */
	private void runTunerChain(String sequencedFullName, String json, Object result) throws Exception {
		ProcessObject po = new ProcessObject();
		po.setSignature(sequencedFullName);
		po.setJson(json);
		po.setObject(result);
		JukeCommandProcessorChain.execute(po);
	}

}
