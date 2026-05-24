package org.juke.framework.proxy;

import org.juke.framework.storage.JukeHelper;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.metadata.JukeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * CGLIB {@link MethodInterceptor} that records/replays every eligible
 * public method of a concrete {@code @Component}/{@code @Service} class,
 * always using the <b>concrete class</b> as the recording identity.
 * <p>
 * If {@code MyClass} implements {@code InterfaceA.a()} and
 * {@code InterfaceB.b()}, both calls are stored as
 * {@code MyClass.a} / {@code MyClass.b} — never under the interface name.
 * <p>
 * Methods excluded by {@link JukeMethodFilter} (Object methods, bridge,
 * synthetic, non-public) are passed straight through to the real target.
 */
public class JukeClassInterceptor implements MethodInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(JukeClassInterceptor.class);

    private final Object realTarget;
    private final Class<?> concreteClass;

    public JukeClassInterceptor(Object realTarget, Class<?> concreteClass) {
        this.realTarget = realTarget;
        this.concreteClass = concreteClass;
    }

    // -----------------------------------------------------------------
    // MethodInterceptor
    // -----------------------------------------------------------------

    @Override
    public Object intercept(Object proxy, Method method, Object[] args,
                            MethodProxy methodProxy) throws Throwable {

        if (!JukeMethodFilter.shouldIntercept(method)) {
            return methodProxy.invoke(realTarget, args);
        }

        String mode = JukeFactory.resolveJukeState(JukeState.JUKE);

        if (JukeState.RECORD.equalsIgnoreCase(mode)) {
            return recordAndForward(method, args, methodProxy);
        }
        if (JukeState.REPLAY.equalsIgnoreCase(mode)) {
            return replay(method, args);
        }
        // IGNORE / NONE — pass through
        return methodProxy.invoke(realTarget, args);
    }

    // -----------------------------------------------------------------
    // Record path
    // -----------------------------------------------------------------

    private Object recordAndForward(Method method, Object[] args,
                                    MethodProxy methodProxy) throws Throwable {
        Object result = methodProxy.invoke(realTarget, args);
        try {
            String methodName = buildMethodName(method);
            String typeDiscriminator = TypeDiscriminatorUtil.extractTypeDiscriminator(method, args);
            String identifier = JukeNameFormatter.buildAndRegister(
                    concreteClass, method, methodName, typeDiscriminator);

            JukeHelper.getInstance().writeToFile(identifier, result);

            // Record input arguments for replay-time validation
            JukeHelper.getInstance().writeInputArgs(identifier, method, args);

            if (typeDiscriminator != null && result != null) {
                JukeHelper.getInstance().writeTypeMetadata(
                        identifier, result.getClass().getCanonicalName());
            }
            LOG.debug("Recorded {}.{}", concreteClass.getSimpleName(), method.getName());
        } catch (Exception e) {
            LOG.warn("Failed to record {}.{}: {}",
                    concreteClass.getSimpleName(), method.getName(), e.getMessage());
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Replay path — delegates to a per-class ReplayHandler
    // -----------------------------------------------------------------

    private Object replay(Method method, Object[] args) throws Throwable {
        return invokeReplay(concreteClass, method, args);
    }

    /**
     * Capture-helper that narrows {@code Class<?>} to a specific {@code Class<T>}
     * so {@code ReplayHandler<T>}'s generic signature can be satisfied. The two
     * unchecked casts are localized here: the cache entry keyed by {@code clazz}
     * is known to hold a {@code ReplayHandler<T>} by construction, and
     * {@code realTarget} is the instance the CGLIB proxy was built from — by
     * definition assignable to {@code clazz}.
     */
    private <T> Object invokeReplay(Class<T> clazz, Method method, Object[] args) throws Throwable {
        @SuppressWarnings("unchecked")
        ReplayHandler<T> handler =
                (ReplayHandler<T>) ReplayHandler.getReplayHandlerCache().get(clazz);
        if (handler == null) {
            @SuppressWarnings("unchecked")
            T typedTarget = (T) realTarget;
            handler = new ReplayHandler<T>(typedTarget, clazz);
        }
        return handler.invoke(null, method, args);
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Builds a method name using the concrete class for overload detection
     * (not the declaring interface).
     */
    private String buildMethodName(Method method) {
        boolean overloaded = JukeParser.isOverloaded(concreteClass, method.getName());
        String name = method.getName();
        if (overloaded) {
            name += JukeParser.buildParameterSignature(method);
        }
        return name;
    }

    // -----------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------

    /**
     * Creates a CGLIB subclass proxy of {@code concreteClass} that
     * intercepts all eligible methods for record/replay.
     * <p>
     * Uses Objenesis to instantiate the proxy without calling any constructor,
     * which is required for Spring Boot 3 beans that use constructor injection
     * and therefore have no no-arg constructor.
     */
    public static <T> T createProxy(T target, Class<T> concreteClass) {
        JukeClassInterceptor interceptor = new JukeClassInterceptor(target, concreteClass);
        return JukeProxyFactory.createClassProxy(concreteClass, interceptor);
    }
}

