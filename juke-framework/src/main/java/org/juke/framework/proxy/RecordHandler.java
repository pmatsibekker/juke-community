package org.juke.framework.proxy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.exception.JukeRecordException;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.metadata.JukeParser;


public class RecordHandler<T> extends BaseHandler<T> {
	private static final Logger LOG = LoggerFactory.getLogger(RecordHandler.class);

	/** ReentrantLock replaces synchronized to avoid pinning Java 21 virtual threads. */
	private final ReentrantLock lock = new ReentrantLock();

	public T newInstance(T service, Class<T> clazz) {
		List<Class<?>> interfaces = new ArrayList<>(Arrays.asList(service.getClass().getInterfaces()));
		if (!interfaces.contains(clazz) && clazz.isInterface()) {
			interfaces.add(clazz);
		}
		Class<?>[] classes = interfaces.toArray(new Class<?>[0]);

		return JukeProxyFactory.createInterfaceProxy(
				clazz,
				classes,
				service.getClass().getClassLoader(),
				new RecordHandler<T>(service, clazz));
	}

	public RecordHandler() {

	}
	public RecordHandler(T service, Class<T> clazz) {
		this.setService(service);
		this.setInterfaceClass(clazz);
		if (!JukeClass.instance().containsKey(clazz.getCanonicalName())) {
			JukeClass JukeClass = JukeConfigBuilder.set(clazz).build();
			JukeClass.instance().put(clazz.getCanonicalName(), JukeClass);
			setJukeClass(JukeClass);
		}
	}
	@Override
	public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
		lock.lock();
		MdcToken mdcToken = pushInvocationMdc(m);
		try {
			if (m.getName().equals("toString")) return super.toString();

			String methodName = JukeParser.getMethodName(this, m);
			String typeDiscriminator = TypeDiscriminatorUtil.extractTypeDiscriminator(m, args);
			boolean isSpecial = isSpecialMethod(m);

			Object result;
			try {
				result = invokeReal(m, args, methodName);
			} catch (Exception e) {
				throw new JukeRecordException("unexpected invocation exception during record of "
						+ this.getInterfaceClass().getName() + "." + methodName, e);
			} finally {
				LOG.debug("after method " + methodName);
			}

			if (result != null && isSpecial) {
				writeSidecars(m, args, result, methodName, typeDiscriminator);
			}
			return result;
		} finally {
			popInvocationMdc(mdcToken);
			lock.unlock();
		}
	}

	/**
	 * A "special" method is one declared on the Juke-managed interface — the
	 * only ones we care to record. Methods that exist only on the concrete
	 * implementation (e.g. package-private helpers the proxy still routes
	 * through) are passed through without a sidecar write.
	 */
	private boolean isSpecialMethod(Method m) {
		List<Method> interfaceMethods = JukeParser.isInMethods(
				m.getName(), this.getInterfaceClass().getMethods());
		boolean special = interfaceMethods != null && !interfaceMethods.isEmpty();
		if (special) {
			LOG.debug(m.getName() + " juke this one need special handling");
		}
		return special;
	}

	/**
	 * Invokes the real wrapped service, matching {@code m} against the
	 * service's declared methods by name and argument assignability.
	 *
	 * @throws JukeAccessException if no assignable overload is found
	 */
	private Object invokeReal(Method m, Object[] args, String methodName) throws Exception {
		List<Method> candidates = JukeParser.isInMethods(
				m.getName(), this.getService().getClass().getMethods());
		for (int i = 0; i < candidates.size(); i++) {
			Method candidate = candidates.get(i);
			if (!JukeParser.isAssignableFromArguments(candidate, args)) continue;
			try {
				return (args == null)
						? candidate.invoke(this.getService())
						: candidate.invoke(this.getService(), args);
			} catch (Exception ignored) {
				// Try next candidate — could be an overload mismatch caught
				// only at invocation time. Preserved from the original loop.
			}
		}
		throw new JukeAccessException("Unable to find interface for "
				+ this.getService().getClass() + "." + methodName);
	}

	/**
	 * Writes the three record-time sidecars: the JSON payload, the input
	 * arguments (for replay-time validation), and — when a {@code Class<?>}
	 * discriminator was observed — the runtime type of the result.
	 */
	private void writeSidecars(Method m, Object[] args, Object result,
			String methodName, String typeDiscriminator) {
		String shortIdentifier = JukeNameFormatter.buildAndRegister(
				this.getInterfaceClass(), m, methodName, typeDiscriminator);
		JukeHelper.getInstance().writeToFile(shortIdentifier, result);
		JukeHelper.getInstance().writeInputArgs(shortIdentifier, m, args);
		if (typeDiscriminator != null) {
			JukeHelper.getInstance().writeTypeMetadata(
					shortIdentifier, result.getClass().getCanonicalName());
		}
	}

}
