package com.example.annotations;

import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Constructor-injection variant of {@link JukeAnnotationExample}.
 * Two fields, two services, both wrapped — demonstrates that
 * constructor injection works the same as setter / field injection
 * under {@code @Juke}: assign the raw bean, let the post-processor
 * swap it for a proxy before exposing this component to consumers.
 *
 * <p>Compare the pre-{@code @Juke} pattern (constructor-side
 * {@code JukeFactory.newInstance(...)} calls per dependency) with the
 * annotation-driven version below — the constructor is back to plain
 * Spring DI, and the wrapping intent is declared at the field.
 */
@Component
public class JukeConstructorExample {

    @Juke("juke")
    protected IGreetingsService greetingService;

    @Juke("juke")
    protected IAnotherService anotherService;

    @Autowired
    public JukeConstructorExample(IGreetingsService greetingService,
                                  IAnotherService anotherService) {
        this.greetingService = greetingService;
        this.anotherService = anotherService;
        // Plain assignment — no manual JukeFactory wrapping required.
        // JukeBeanPostProcessor inspects the @Juke fields and replaces
        // them with proxies after this constructor returns.
    }

    public Greeting processGreeting(String name) {
        return greetingService.greeting(name);
    }

    public String processAnotherRequest() {
        return anotherService.processRequest();
    }
}
