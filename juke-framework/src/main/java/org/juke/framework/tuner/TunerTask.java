package org.juke.framework.tuner;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.juke.framework.runtime.JukeRuntimeHolder;
public abstract class TunerTask {


	/** Must be set during derived class constructor to {@code this.getClass()}. */
	public Class<? extends TunerTask> tuner = null;

	/**
	 * Returns the shared participants map keyed by tuner class name.
	 * <p>
	 * Phase 3 Step F — the old {@code public static Map} was replaced by a
	 * view backed by {@link org.juke.framework.runtime.JukeRuntime#tunerParticipants()}.
	 * Direct field access is gone; callers must go through this accessor.
	 */
	public static Map<String,Set<String>> getParticipants() {
		return JukeRuntimeHolder.current().tunerParticipants();
	}

	/**
	 * Replaces the current participants map contents with {@code p}. Preserved
	 * for test callers that relied on the legacy setter; copies entries into
	 * the runtime-scoped map so the reference held by the runtime holder is
	 * not broken.
	 */
	public static void setParticipants(Map<String,Set<String>> p) {
		Map<String,Set<String>> target = getParticipants();
		target.clear();
		if (p != null) {
			target.putAll(p);
		}
	}
	public static void add(TunerTask tunerTask, String sequencedItem) {
		TunerTaskRegistry.register(tunerTask);
		String className=tunerTask.getClass().getCanonicalName();
		Map<String,Set<String>> participants = getParticipants();
		if (participants.containsKey(className)) {
			participants.get(className).add(sequencedItem);
		}else {
			Set<String> participantSet = ConcurrentHashMap.newKeySet();
			participantSet.add(sequencedItem);
			participants.put(className, participantSet);
		}

	}

	public static void clear() {
		getParticipants().forEach((key,value) ->{
			value.clear();
		});
	}
	public final void executeWith(ProcessObject obj) throws Exception {
		if (this.tuner == null) {
			throw new Exception("Failed to find a registered Tuner class. Add this.tuner = this.getClass(); into Tuner constructor");
		}
		Set<String> forTuner = getParticipants().get(this.tuner.getCanonicalName());
		if (forTuner != null && forTuner.contains(obj.getSignature())) {
			execute(obj);
		}
	}
	public abstract void execute(ProcessObject obj) throws Exception;


}
