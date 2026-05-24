package com.example.greeting;

import org.juke.framework.annotation.JukeController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@code GET /greeting?name=…}. The controller is thin — all the
 * Juke wiring lives one layer down in {@link JukeGreetingsDAO}, which is
 * where {@code @Juke} actually replaces the upstream service with its
 * proxy.
 */
@JukeController
@RestController
public class JukeGreetingController {

    @Autowired
    private JukeGreetingsDAO dao;

    @GetMapping("/greeting")
    public Greeting greeting(@RequestParam(value = "name", defaultValue = "World") String name) {
        return dao.greeting(name);
    }
}
