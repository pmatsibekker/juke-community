package com.example.controllerdemo;

import org.juke.framework.annotation.JukeController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The controller under Juke's advice. {@code @JukeController} tells the
 * framework to capture this controller's request/response and diff them on
 * replay. The response is deterministic in the input, so a replay with the
 * <em>same</em> input matches the baseline (no finding) and a replay with a
 * <em>different</em> input deviates (CONTROLLER_MISMATCH).
 */
@RestController
@JukeController
@RequestMapping("/api")
public class GreetController {

    public record Greeting(String message) {}

    @GetMapping("/greet/{name}")
    public Greeting greet(@PathVariable String name) {
        return new Greeting("Hello, " + name + "!");
    }
}
