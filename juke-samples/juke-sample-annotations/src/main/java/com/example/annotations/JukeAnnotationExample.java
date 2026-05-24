package com.example.annotations;

import jakarta.annotation.PostConstruct;
import org.juke.framework.annotation.Juke;
import org.juke.framework.annotation.JukeInitializer;
import org.springframework.stereotype.Component;

/**
 * Reference for the three ways to apply {@code @Juke}:
 *
 * <ol>
 *   <li><b>Field-level</b> — {@code @Juke} on a field. The
 *       {@code JukeBeanPostProcessor} replaces the bean reference with
 *       a proxy after Spring finishes injecting.</li>
 *   <li><b>Method-level</b> — {@code @Juke("juke")} on a method. The
 *       annotation processor generates code to wrap the return value
 *       with {@code JukeFactory} on the way out.</li>
 *   <li><b>Manual</b> — call {@link JukeInitializer#wrap(Object, Class, String)}
 *       directly from {@code @PostConstruct} when you need a wrapping
 *       moment the framework's automatic flow doesn't cover.</li>
 * </ol>
 */
@Component
public class JukeAnnotationExample {

    // (1) Field-level: JukeBeanPostProcessor replaces this after injection.
    @Juke
    private IGreetingsService greetingService;

    public JukeAnnotationExample(IGreetingsService greetingService) {
        this.greetingService = greetingService;
    }

    // (2) Method-level: the annotation processor wraps the return value
    //     with JukeFactory on the way out.
    @Juke("juke")
    public IGreetingsService getWrappedGreetingService() {
        return this.greetingService;
    }

    // (3) Manual: the escape hatch for cases where you need to wrap a
    //     reference at a moment the auto-flow doesn't cover.
    @PostConstruct
    public void initializeJukeAnnotations() {
        // Walks the class, applies the field-level @Juke wrapping
        // imperatively (equivalent to what the bean post-processor does
        // automatically — useful inside non-managed objects).
        JukeInitializer.initializeAnnotatedFields(this);

        // Direct wrap: you choose the state and the type explicitly.
        this.greetingService = JukeInitializer.wrap(
                this.greetingService,
                IGreetingsService.class,
                "juke"
        );
    }
}
