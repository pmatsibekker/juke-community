package org.juke.framework.proxy;

import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.objenesis.ObjenesisStd;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Unified factory for every proxy kind Juke needs to manufacture. Phase 6
 * (Open/Closed) consolidates proxy-creation code that was previously
 * duplicated across {@link RecordHandler#newInstance(Object, Class)},
 * {@link ReplayHandler#newInstance(Class)},
 * {@link JukeClassInterceptor#createProxy(Object, Class)},
 * {@link SessionAwareReplayHandler#newProxy}, and
 * {@link TemplateRecordingWrapper#wrap}.
 *
 * <p>Each method encapsulates exactly one unavoidable unchecked cast at a
 * well-known seam — callers no longer need their own {@code @SuppressWarnings}.
 *
 * <h3>Proxy kinds</h3>
 * <ul>
 *   <li><b>Interface proxy</b> — JDK {@link Proxy} for a single interface
 *       (most common case).</li>
 *   <li><b>Multi-interface proxy</b> — JDK {@link Proxy} over every interface
 *       implemented by a concrete class, declared-type narrowing applied at
 *       the call site.</li>
 *   <li><b>Class proxy</b> — CGLIB subclass proxy for concrete classes,
 *       instantiated via Objenesis so constructors are never called (required
 *       for Spring beans with constructor injection).</li>
 * </ul>
 */
public final class JukeProxyFactory {

    private JukeProxyFactory() {
    }

    /**
     * Creates a JDK dynamic proxy for a single interface.
     *
     * @param iface   the interface the proxy should implement
     * @param handler the invocation handler
     * @param <T>     the interface type
     * @return a proxy instance implementing {@code iface}
     */
    public static <T> T createInterfaceProxy(Class<T> iface, InvocationHandler handler) {
        // Proxy.newProxyInstance returns Object. The proxy is built from
        // exactly one interface — {@code iface} — so the cast to T is safe.
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                handler);
        return proxy;
    }

    /**
     * Creates a JDK dynamic proxy for multiple interfaces, with the caller
     * asserting the resulting proxy is assignable to {@code declaredType}.
     *
     * <p>Used when a concrete service must be exposed as a proxy assignable
     * to one of its interfaces (or to an interface-typed field).
     *
     * @param declaredType the type the caller will assign the result to;
     *                     must be implemented by the proxy (either directly
     *                     in {@code interfaces} or by a super-interface)
     * @param interfaces   the set of interfaces the proxy implements
     * @param classLoader  loader to define the proxy class against
     * @param handler      the invocation handler
     * @param <T>          the declared type
     * @return a proxy instance
     */
    public static <T> T createInterfaceProxy(Class<T> declaredType,
                                             Class<?>[] interfaces,
                                             ClassLoader classLoader,
                                             InvocationHandler handler) {
        // Proxy.newProxyInstance returns Object. The caller's
        // {@code declaredType} is expected to be one of {@code interfaces}
        // (or a super-interface of one of them); the cast echoes that contract.
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(classLoader, interfaces, handler);
        return proxy;
    }

    /**
     * Creates a CGLIB subclass proxy of {@code concreteClass} that routes all
     * calls through {@code interceptor}. Instantiation uses Objenesis so no
     * constructor of {@code concreteClass} is invoked — required for Spring
     * Boot 3 beans that rely on constructor injection.
     *
     * @param concreteClass the class the proxy should subclass
     * @param interceptor   the CGLIB interceptor wired as the sole callback
     * @param <T>           the concrete class type
     * @return a proxy instance assignable to {@code concreteClass}
     */
    public static <T> T createClassProxy(Class<T> concreteClass, MethodInterceptor interceptor) {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(concreteClass);
        // Register the callback type only — never call create(), which would
        // require a no-arg constructor.
        enhancer.setCallbackType(MethodInterceptor.class);
        Class<?> proxyClass = enhancer.createClass();

        // Objenesis returns Object; the Enhancer-generated proxyClass extends
        // {@code concreteClass}, so narrowing to T is safe.
        @SuppressWarnings("unchecked")
        T proxy = (T) new ObjenesisStd().newInstance(proxyClass);

        // Wire the interceptor via the CGLIB Factory contract.
        ((Factory) proxy).setCallbacks(new Callback[]{interceptor});
        return proxy;
    }

    /**
     * Collects every interface implemented by {@code clazz} or any of its
     * ancestors (stopping at {@code Object}). Useful for building JDK proxies
     * over concrete classes whose interface set is not known at compile time.
     */
    public static Class<?>[] collectInterfaces(Class<?> clazz) {
        Set<Class<?>> interfaces = new LinkedHashSet<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            interfaces.addAll(Arrays.asList(current.getInterfaces()));
            current = current.getSuperclass();
        }
        return interfaces.toArray(new Class<?>[0]);
    }
}
