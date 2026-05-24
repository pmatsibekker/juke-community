package org.juke.framework.tuner;


import java.util.Collection;
import java.util.Hashtable;


public class JukeCommandProcessorChain {
public static JukeCommandProcessorChain instance = null;

private Hashtable<String, TunerTask> transformers = new Hashtable<>();



static {
	initialize();
}

public static void execute(ProcessObject obj) throws Exception{
	
	TunerTask[] tuners=TunerTaskRegistry.getTuners().toArray(new TunerTask[TunerTaskRegistry.getTuners().size()]);
	for (int i=0; i < tuners.length; i++) {
	
		tuners[i].executeWith(obj);
	}

	
}

public static JukeCommandProcessorChain getInstance() {
	return instance;
}

public static void setInstance(JukeCommandProcessorChain instance) {
	JukeCommandProcessorChain.instance = instance;
}



public static void initialize() {
	instance = new JukeCommandProcessorChain();
	
}


}
