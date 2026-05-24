package org.juke.framework.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.juke.framework.metadata.JukeClass;
import org.slf4j.MDC;

public abstract class BaseHandler<T> implements InvocationHandler {

	/** MDC keys populated for the duration of a Juke proxy invocation. */
	public static final String MDC_JUKE_METHOD = "jukeMethod";
	public static final String MDC_JUKE_TARGET = "jukeTarget";

	boolean isInitialized = false;
	private T service;
	private Class<T> interfaceClass;
	private JukeClass JukeClass;

	/**
	 * Pushes method-scoped diagnostic context for a proxy invocation.
	 * Returns a token the caller must pass to {@link #popInvocationMdc} in
	 * a finally block to avoid leaking entries across threads.
	 */
	protected final MdcToken pushInvocationMdc(Method method) {
		Class<?> target = getInterfaceClass();
		String prevMethod = MDC.get(MDC_JUKE_METHOD);
		String prevTarget = MDC.get(MDC_JUKE_TARGET);
		if (method != null) {
			MDC.put(MDC_JUKE_METHOD, method.getName());
		}
		if (target != null) {
			MDC.put(MDC_JUKE_TARGET, target.getSimpleName());
		}
		return new MdcToken(prevMethod, prevTarget);
	}

	protected final void popInvocationMdc(MdcToken token) {
		if (token == null) return;
		restore(MDC_JUKE_METHOD, token.prevMethod);
		restore(MDC_JUKE_TARGET, token.prevTarget);
	}

	private static void restore(String key, String prev) {
		if (prev == null) {
			MDC.remove(key);
		} else {
			MDC.put(key, prev);
		}
	}

	/** Snapshot of previous MDC values so nested invocations don't clobber each other. */
	protected static final class MdcToken {
		private final String prevMethod;
		private final String prevTarget;
		private MdcToken(String prevMethod, String prevTarget) {
			this.prevMethod = prevMethod;
			this.prevTarget = prevTarget;
		}
	}
	
	public JukeClass getJukeClass() {
		return JukeClass;
	}
	public void setJukeClass(JukeClass JukeClass) {
		this.JukeClass = JukeClass;
	}
	public boolean isInitialized() {
		return isInitialized;
	}
	public void setInitialized(boolean isInitialized) {
		this.isInitialized = isInitialized;
	}
	public T getService() {
		return service;
	}
	public void setService(T service) {
		this.service = service;
	}
	public Class<T> getInterfaceClass() {
		return interfaceClass;
	}
	public void setInterfaceClass(Class<T> interfaceClass) {
		this.interfaceClass = interfaceClass;
	}

	

	 

}
