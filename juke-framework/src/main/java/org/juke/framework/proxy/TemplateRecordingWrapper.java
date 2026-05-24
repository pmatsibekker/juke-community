package org.juke.framework.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a Spring Template object (RestTemplate, JdbcTemplate, etc.) for Juke
 * recording and playback, all calls routed through {@link TemplateMethodInterceptor}.
 * <p>
 * The proxy kind is chosen from the declared field type:
 * <ul>
 *   <li><b>Interface-typed field</b> → a JDK dynamic proxy implementing that interface.</li>
 *   <li><b>Concrete-typed field</b> (e.g. {@code RestTemplate restTemplate}) → a CGLIB
 *       subclass (via {@link JukeProxyFactory#createClassProxy}) that delegates through the
 *       interceptor. A subclass <em>is</em> an instance of the concrete type, so the wrapper
 *       is assignable back to the field.</li>
 *   <li><b>Un-subclassable concrete class</b> (e.g. {@code final}) → a JDK proxy over the
 *       implemented interfaces (assignable only to an interface-typed field), else the
 *       unwrapped instance.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * RestTemplate real = new RestTemplate();
 * RestTemplate wrapped = TemplateRecordingWrapper.wrap(
 *         real, "RestTemplate", "record", new String[0], RestTemplate.class);
 * </pre>
 *
 * @see TemplateMethodInterceptor
 * @see JukeProxyFactory
 */
public class TemplateRecordingWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateRecordingWrapper.class);

    private TemplateRecordingWrapper() {
    }

    /**
     * Wraps the given template with Juke interception.
     * <p>
     * Strategy:
     * <ol>
     *     <li>Interface-typed field → JDK dynamic proxy implementing that interface.</li>
     *     <li>Concrete-typed field → CGLIB subclass of the concrete type that delegates
     *         through the same interceptor. A subclass <em>is</em> an instance of the
     *         concrete type, so the result is assignable back to a concrete field such as
     *         {@code RestTemplate restTemplate}. A JDK interface proxy is <em>not</em>
     *         assignable to a concrete field — using one would fail at {@code field.set}.</li>
     *     <li>If the concrete type is un-subclassable (e.g. {@code final}), fall back to a
     *         JDK proxy over its interfaces — usable only when the field is interface-typed.</li>
     * </ol>
     *
     * @param template        the real template instance
     * @param templateName    logical name for recording identifiers
     * @param jukeState       "record", "replay", or "juke"
     * @param extraExclusions methods to exclude from interception
     * @param targetType      the declared field type (may be a concrete class like RestTemplate)
     * @param <T>             the template type
     * @return wrapped template instance
     */
    public static <T> T wrap(T template,
                             String templateName,
                             String jukeState,
                             String[] extraExclusions,
                             Class<T> targetType) {

        TemplateMethodInterceptor interceptor = new TemplateMethodInterceptor(
                template, templateName, jukeState, extraExclusions);

        // Interface-typed field → standard JDK dynamic proxy.
        if (targetType.isInterface()) {
            return JukeProxyFactory.createInterfaceProxy(targetType, interceptor);
        }

        // Concrete-typed field → CGLIB subclass delegating through the same interceptor,
        // so the wrapper is assignable to the concrete field type. The interceptor's
        // invoke() ignores the proxy argument and forwards to the real template, so no
        // proxy state is needed (Objenesis builds it without a constructor).
        try {
            org.springframework.cglib.proxy.MethodInterceptor cglib =
                    (obj, method, args, methodProxy) -> interceptor.invoke(obj, method, args);
            T proxy = JukeProxyFactory.createClassProxy(targetType, cglib);
            LOG.debug("Created CGLIB subclass wrapper for {} ({})", templateName, targetType.getSimpleName());
            return proxy;
        } catch (Exception cglibFailure) {
            // Un-subclassable (e.g. a final class). Fall back to a JDK proxy over the
            // implemented interfaces — assignable only if the field is interface-typed.
            Class<?>[] interfaces = JukeProxyFactory.collectInterfaces(template.getClass());
            if (interfaces.length > 0) {
                LOG.warn("Could not CGLIB-subclass {} ({}): {}. Falling back to a JDK interface "
                                + "proxy — this only works if the field is interface-typed.",
                        templateName, targetType.getSimpleName(), cglibFailure.getMessage());
                return JukeProxyFactory.createInterfaceProxy(
                        targetType,
                        interfaces,
                        template.getClass().getClassLoader(),
                        interceptor);
            }
            LOG.warn("Cannot wrap {} — class is not subclassable and implements no interfaces. "
                    + "Returning the unwrapped template.", templateName);
            return template;
        }
    }
}

