package com.example.statusgrid;

import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drives the seam. {@code @Juke} records globally and replays per
 * cookie session, so each request carrying a {@code JUKE_SESSION} cookie
 * advances that session's position in the recording.
 */
@RestController
@RequestMapping("/api")
public class GreetController {

    @Juke
    @Autowired
    GreetingService greetingService;

    @GetMapping("/greet/{name}")
    public String greet(@PathVariable String name) {
        return greetingService.greet(name);
    }
}
