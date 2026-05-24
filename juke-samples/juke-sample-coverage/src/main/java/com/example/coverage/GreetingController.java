package com.example.coverage;

import org.juke.framework.annotation.JukeController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface for the demo application.
 *
 * <ul>
 *   <li>{@code GET /api/styles} — the three styles the picker offers</li>
 *   <li>{@code GET /api/greeting?name=&amp;style=} — generates a greeting,
 *       delegating to the {@code @Juke}-mediated DAO</li>
 * </ul>
 *
 * <p>The {@code /service/coverage} family is exposed automatically by the
 * {@code juke-coverage} module and lives on the same origin.
 */
@JukeController
@RestController
@RequestMapping("/api")
public class GreetingController {

    @Autowired
    private GreetingService service;

    @GetMapping("/styles")
    public List<String> styles() {
        return service.styles();
    }

    @GetMapping("/greeting")
    public Greeting greeting(@RequestParam(value = "name", defaultValue = "") String name,
                             @RequestParam(value = "style", defaultValue = "casual") String style) {
        return service.greet(name, style);
    }
}
