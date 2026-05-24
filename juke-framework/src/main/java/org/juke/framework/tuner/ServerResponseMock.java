package org.juke.framework.tuner;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerResponseMock {

	// Phase 5.5 — ConcurrentHashMap so concurrent registerException /
	// getException calls (parallel test runs, multi-session record/replay)
	// stay safe. Reference is final; only the contents mutate via
	// registerException(...). Was a plain HashMap.
	private static final Map<String, Exception> exceptionMap = new ConcurrentHashMap<>();
	static {
		
		registerException(NullPointerException.class.getSimpleName(),new NullPointerException());
		registerException(InvocationTargetException.class.getSimpleName(),new InvocationTargetException(null));
		registerException(Exception.class.getSimpleName(),new Exception());
		registerException(IOException.class.getSimpleName(),new IOException());	
		registerException(IllegalAccessException.class.getSimpleName(),new IllegalAccessException());
		registerException(ParseException.class.getSimpleName(),new ParseException("",0));
	}
	
	
	Exception exception;
	
	
	public ServerResponseMock(String exceptionClass, String response) throws Exception {
		Exception e = lookup(exceptionClass);
		if (e == null) e = new Exception();
		this.exception = e;
		throw e;
	}




	public static void registerException(String name, Exception e) {
		exceptionMap.put(name,e);
	}
	public static Exception getException(String name) {
		Exception e = lookup(name);
		if (e == null)
			e = lookup("Exception");
		return e;
	}

	/**
	 * Null-safe wrapper around the {@link ConcurrentHashMap}-backed map —
	 * Phase 5.5 swapped the underlying map from {@link java.util.HashMap}
	 * to {@code ConcurrentHashMap} for thread safety, and the new map
	 * rejects null keys with {@code NullPointerException}. Callers that
	 * pass a null name should still get the documented fallback semantics.
	 */
	private static Exception lookup(String name) {
		return name == null ? null : exceptionMap.get(name);
	}
	
	
}
