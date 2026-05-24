package org.juke.framework.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.juke.framework.runtime.JukeRuntimeHolder;

public class JukeClass {

String className = null;
List<JukeMethod> methods=new ArrayList<JukeMethod>();

/**
 * Returns the shared metadata cache keyed by canonical class name.
 * <p>
 * Phase 3 Step F — the old {@code static HashMap} lived on this class; it now
 * lives on {@link org.juke.framework.runtime.JukeRuntime#metadataCache()} so
 * that parallel sessions can each own an isolated cache. The method signature
 * is preserved so existing callers keep working.
 */
public static Map<String,JukeClass> instance() {
	return JukeRuntimeHolder.current().metadataCache();
}
public String getClassName() {
	return className;
}

public void setClassName(String className) {
	this.className = className;
}
public List<JukeMethod> getMethods() {
	return methods;
}

public void setMethods(List<JukeMethod> methods) {
	this.methods = methods;
}

public  List<JukeMethod> getMethodsByName(String name) {
	 List<JukeMethod> result= (List<JukeMethod>) this.getMethods().stream()
			.filter(method -> method.getMethod().equals(name)).collect(Collectors.toList());

	 return result;

}

}
