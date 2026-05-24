package org.juke.framework.tuner;


import java.util.Collection;
import java.util.HashMap;

import java.util.Map;

public class TunerTaskRegistry  {
	public static Map<String, TunerTask> tuners=new HashMap<String, TunerTask>();
	
	public static void register(TunerTask tuner) {
		if (!tuners.containsKey(tuner.getClass().getCanonicalName()))
			tuners.put(tuner.getClass().getCanonicalName(),tuner);
		
		
	}
	public static TunerTask getTuner(String name) {
		return tuners.get(name);
	}
	public static boolean contains(String name) {
		return tuners.containsKey(name);
	}
	public static Collection<TunerTask> getTuners() {
		return tuners.values();
	}
	
}
