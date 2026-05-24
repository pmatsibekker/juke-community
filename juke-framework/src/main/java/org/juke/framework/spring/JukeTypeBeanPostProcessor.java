package org.juke.framework.spring;

import org.juke.framework.annotation.Juke;
import org.juke.framework.coverage.JukeMockRegistry;
import org.juke.framework.proxy.JukeClassInterceptor;
import org.juke.framework.proxy.JukeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Detects Spring beans whose <b>class</b> is annotated with {@code @Juke} and
 * wraps them in a CGLIB subclass proxy so that every eligible public method
 * is recorded or replayed under the <em>concrete class</em> identity.
 * <p>
 * This is complementary to {@link JukeBeanPostProcessor} which handles
 * {@code @Juke} on <em>fields</em> (interface-based JDK proxies).
 *
 * <h3>Example</h3>
 * <pre>
 * &#64;Juke
 * &#64;Service
 * public class OrderService implements Billable, Shippable {
 *     public Invoice bill()  { … }   // recorded as OrderService.bill
 *     public Label   ship()  { … }   // recorded as OrderService.ship
 * }
 * </pre>
 */
public class JukeTypeBeanPostProcessor implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JukeTypeBeanPostProcessor.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {

        Class<?> beanClass = unwrapProxyClass(bean.getClass());

        Juke juke = beanClass.getAnnotation(Juke.class);
        if (juke == null) {
            return bean;
        }

        // Only wrap when the global state is RECORD or REPLAY (or "juke")
        String mode = resolveMode(juke.value());
        if (JukeState.IGNORE.equalsIgnoreCase(mode)
                || JukeState.NONE.equalsIgnoreCase(mode)) {
            LOG.debug("@Juke on {} ignored — mode is {}", beanClass.getSimpleName(), mode);
            return bean;
        }

        LOG.info("Wrapping @Juke bean '{}' ({}) with CGLIB proxy",
                beanName, beanClass.getName());

        // Record the class-level seam so coverage tooling excludes this
        // concrete class — in replay mode its recorded methods are served
        // from the ZIP, not executed. See JukeMockRegistry.
        JukeMockRegistry.recordTypeMock(beanClass);

        // beanClass is Class<?> from Spring; the CGLIB proxy is subtype-safe,
        // and the return type is Object, so the capture to Class<Object> only
        // affects the generic type-witness for createProxy.
        @SuppressWarnings("unchecked")
        Class<Object> erased = (Class<Object>) beanClass;
        return JukeClassInterceptor.createProxy(bean, erased);
    }

    // ------------------------------------------------------------------

    /**
     * If the bean is already a Spring CGLIB proxy (class name contains $$),
     * return its real superclass.
     */
    private Class<?> unwrapProxyClass(Class<?> clazz) {
        if (clazz.getName().contains("$$")) {
            Class<?> sup = clazz.getSuperclass();
            if (sup != null && sup != Object.class) {
                return sup;
            }
        }
        return clazz;
    }

    private String resolveMode(String annotationValue) {
        // Resolve against the global state, same logic as JukeFactory
        return org.juke.framework.proxy.JukeFactory.resolveJukeState(
                annotationValue == null || annotationValue.isEmpty()
                        ? JukeState.JUKE : annotationValue);
    }
}

