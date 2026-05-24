package org.juke.framework.spring;

import org.juke.framework.annotation.Juke;
import org.juke.framework.coverage.JukeMockRegistry;
import org.juke.framework.exception.JukeConfigurationException;
import org.juke.framework.proxy.JukeFactory;
import org.juke.framework.proxy.JukeMethodFilter;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.proxy.TemplateRecordingWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Spring BeanPostProcessor that automatically wraps fields annotated with @Juke
 * after dependency injection is complete.
 * 
 * This processor runs after all @Autowired fields have been injected and
 * automatically wraps them with JukeFactory if they are annotated with @Juke.
 * 
 * Note: This class is registered as a bean in JukeConfiguration and should not be
 * annotated with @Component to avoid duplicate bean definitions.
 */
public class JukeBeanPostProcessor implements BeanPostProcessor {
    
    private static final Logger LOG = LoggerFactory.getLogger(JukeBeanPostProcessor.class);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        processJukeAnnotations(bean);
        return bean;
    }

    /**
     * Processes all @Juke annotated fields in the given bean and wraps them with JukeFactory.
     * 
     * @param bean the Spring bean to process
     */
    private void processJukeAnnotations(Object bean) {
        if (bean == null) {
            return;
        }

        Class<?> clazz = bean.getClass();
        
        // Handle Spring proxies - get the actual target class
        if (clazz.getName().contains("$$")) {
            // This is likely a Spring proxy, try to get the target class
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != Object.class) {
                clazz = superClass;
            }
        }

        // Process all fields in the class hierarchy
        while (clazz != null && clazz != Object.class) {
            processClassFields(bean, clazz);
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * Processes fields in a specific class.
     * 
     * @param bean the bean instance
     * @param clazz the class to process
     */
    private void processClassFields(Object bean, Class<?> clazz) {
        for (Field field : clazz.getDeclaredFields()) {
            Juke jukeAnnotation = field.getAnnotation(Juke.class);
            if (jukeAnnotation != null) {
                processJukeField(bean, field, jukeAnnotation);
            }
        }
    }

    /**
     * Processes a single @Juke annotated field.
     * 
     * @param bean the bean instance
     * @param field the field to process
     * @param jukeAnnotation the @Juke annotation
     */
    private void processJukeField(Object bean, Field field, Juke jukeAnnotation) {
        try {
            field.setAccessible(true);
            Object fieldValue = field.get(bean);
            if (!shouldWrap(bean, field, fieldValue, jukeAnnotation)) {
                return;
            }

            String normalizedState = normalizeJukeState(jukeAnnotation.value());

            // Record the seam Juke is about to install: the declared interface
            // and the concrete implementation being displaced. This is the
            // authoritative input for coverage exclusion — in replay mode the
            // displaced impl is never executed, so coverage tooling must not
            // count it. The developer never names the impl; Juke knows it
            // because it is holding the real bean right here.
            JukeMockRegistry.recordFieldMock(field.getType(), fieldValue.getClass());

            Object wrapped = field.getType().isInterface()
                    // Interface field → lazy JDK proxy, re-resolved per request so
                    // cookie-scoped session replay works.
                    ? buildLazyProxy(fieldValue, field.getType(), normalizedState)
                    // Concrete field → CGLIB subclass that delegates to the real bean,
                    // honoring name()/excludeMethods().
                    : wrapConcreteField(fieldValue, field.getType(), normalizedState, jukeAnnotation);
            field.set(bean, wrapped);

            LOG.debug("Wrapped @Juke field {} in {} ({}) using state: {}",
                    field.getName(), bean.getClass().getSimpleName(),
                    field.getType().isInterface() ? "JDK proxy" : "CGLIB subclass", normalizedState);

        } catch (IllegalAccessException e) {
            LOG.error("Failed to process @Juke annotation on field {} in {}: {}",
                    field.getName(), bean.getClass().getSimpleName(), e.getMessage());
            throw new JukeConfigurationException("Failed to process @Juke annotation on "
                    + bean.getClass().getSimpleName() + "." + field.getName(), e);
        } catch (Exception e) {
            LOG.error("Unexpected error processing @Juke annotation on field {} in {}: {}",
                    field.getName(), bean.getClass().getSimpleName(), e.getMessage());
            throw new JukeConfigurationException("Failed to process @Juke annotation on "
                    + bean.getClass().getSimpleName() + "." + field.getName(), e);
        }
    }

    /**
     * Whether this field is eligible for auto-wrapping. Returns {@code false}
     * for fields that explicitly opt out ({@code autoWrap=false}) or are null;
     * otherwise {@code true}. An interface-typed field is wrapped with a JDK
     * dynamic proxy; a concrete-typed field with a CGLIB subclass (see
     * {@link #processJukeField}).
     */
    private boolean shouldWrap(Object bean, Field field, Object fieldValue, Juke jukeAnnotation) {
        String owner = bean.getClass().getSimpleName();
        if (!jukeAnnotation.autoWrap()) {
            LOG.debug("Auto-wrapping disabled for field {} in {}", field.getName(), owner);
            return false;
        }
        if (fieldValue == null) {
            LOG.warn("Field {} in {} is null, cannot wrap with Juke", field.getName(), owner);
            return false;
        }
        return true;
    }

    /**
     * Wraps a concrete-typed {@code @Juke} field in a CGLIB subclass that
     * delegates to the real bean, honoring {@link Juke#name()} (recording-identity
     * prefix, defaulting to the type's simple name) and {@link Juke#excludeMethods()}.
     * Reuses the shared {@link TemplateRecordingWrapper} for concrete-field
     * interception.
     *
     * <p>The single unchecked cast is justified: the field's value is by
     * definition assignable to its declared type.
     */
    private <T> T wrapConcreteField(Object fieldValue, Class<T> type, String state, Juke jukeAnnotation) {
        @SuppressWarnings("unchecked")
        T original = (T) fieldValue;
        String name = jukeAnnotation.name();
        String recordingName = (name != null && !name.isEmpty()) ? name : type.getSimpleName();
        return TemplateRecordingWrapper.wrap(original, recordingName, state, jukeAnnotation.excludeMethods(), type);
    }

    /**
     * Builds a lazy per-request delegating proxy rather than an eager startup proxy.
     *
     * <p>Why: {@link JukeFactory#newInstance} contains a session-context check that
     * reads {@code JukeSessionContext} (a request-scoped bean populated by the
     * {@code JukeCookieFilter}). If we called {@code newInstance()} once at startup
     * — outside any HTTP request — the session check would always miss, and the
     * resulting proxy would be locked to the global {@code ReplayHandler} forever,
     * making cookie-based per-session replay impossible.
     *
     * <p>By deferring the {@code newInstance()} call to each method invocation, it
     * runs inside the live request thread where the session context is populated,
     * giving correct routing per request:
     * <ul>
     *   <li>{@code JUKE_SESSION_ID + JUKE_TRACK} cookies present → {@code SessionAwareReplayHandler}</li>
     *   <li>No cookies and state = {@code "none"} → real service (pass-through)</li>
     *   <li>No cookies and state = {@code "juke"}/{@code "replay"} → global {@code ReplayHandler}</li>
     * </ul>
     */
    private Object buildLazyProxy(Object originalValue, Class<?> interfaceType, String state) {
        return Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[] { interfaceType },
                (proxy, method, args) -> {
                    if (!JukeMethodFilter.shouldIntercept(method)) {
                        // Bypass Juke entirely for Object utility methods.
                        return invokeUnwrapped(originalValue, method, args);
                    }
                    // Re-resolve the handler on every invocation so the session-context
                    // check in JukeFactory runs inside the live HTTP request thread.
                    Object resolved = resolveJukeProxy(originalValue, interfaceType, state);
                    return invokeUnwrapped(resolved, method, args);
                });
    }

    /**
     * Capture-helper that bridges the {@code Class<?>} wildcard at this call
     * site to {@code JukeFactory<T>}'s {@code Class<T>} signature. The single
     * unchecked cast is justified: the caller's invariant (a {@code @Juke}
     * field's declared type matches the interface it will be proxied under)
     * guarantees the original value is assignable to the interface.
     */
    private <T> T resolveJukeProxy(Object originalValue, Class<T> interfaceType, String state) {
        @SuppressWarnings("unchecked")
        T original = (T) originalValue;
        return new JukeFactory<T>().newInstance(original, interfaceType, state);
    }

    /**
     * Invokes {@code method} on {@code target}, unwrapping the reflective
     * {@link InvocationTargetException} so callers see the real cause.
     */
    private Object invokeUnwrapped(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    /**
     * Normalizes the JukeState value to match the constants in JukeState class.
     * 
     * @param stateValue the state value from the annotation
     * @return the normalized state value
     */
    private String normalizeJukeState(String stateValue) {
        if (stateValue == null || stateValue.trim().isEmpty()) {
            return JukeState.JUKE;
        }
        
        String normalized = stateValue.trim().toLowerCase();
        switch (normalized) {
            case "juke":
                return JukeState.JUKE;
            case "record":
                return JukeState.RECORD;
            case "replay":
                return JukeState.REPLAY;
            case "ignore":
                return JukeState.IGNORE;
            case "none":
            case "":
                return JukeState.NONE;
            case "disable":
                return JukeState.DISABLE;
            default:
                LOG.warn("Unknown JukeState value: {}, using default: {}", stateValue, JukeState.JUKE);
                return JukeState.JUKE;
        }
    }
}