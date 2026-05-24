package org.juke.framework.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Standard Spring idiom for accessing the {@link ApplicationContext} from
 * static utility methods (such as {@link org.juke.framework.proxy.JukeFactory})
 * without requiring a full refactoring of those classes from static to
 * instance-based.
 * <p>
 * Registered as a Spring bean by
 * {@link org.juke.framework.config.JukeConfiguration}.
 * Once the application context is set, static callers can obtain Spring beans
 * via {@link #get()}.
 */
public class JukeSpringContextHolder implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        context = applicationContext;
    }

    /**
     * Returns the Spring {@link ApplicationContext}, or {@code null} if the
     * application has not yet started (or is running outside of Spring).
     */
    public static ApplicationContext get() {
        return context;
    }
}
