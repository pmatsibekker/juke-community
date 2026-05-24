package com.example.greeting;

import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * DAO that demonstrates field-level {@link Juke @Juke} usage:
 * {@code greetingService} is annotated, so the
 * {@code JukeBeanPostProcessor} wraps it with a recording or replay
 * proxy depending on the active mode. The controller calls this DAO,
 * the DAO calls the proxy, the proxy calls (or replays from) the real
 * service — nothing in this class is aware of which mode is active.
 */
@Service
public class JukeGreetingsDAO {

    @Juke("juke")
    IGreetingsService greetingService;

    @Autowired
    public JukeGreetingsDAO(IGreetingsService greetingService) {
        // Field-level @Juke + constructor injection: Spring assigns the
        // raw bean here, then JukeBeanPostProcessor replaces it with the
        // appropriate proxy before this bean is exposed to consumers.
        this.greetingService = greetingService;
    }

    public Greeting greeting(String name) {
        return greetingService.greeting(name);
    }
}
