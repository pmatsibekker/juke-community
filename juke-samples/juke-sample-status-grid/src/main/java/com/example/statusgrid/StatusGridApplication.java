package com.example.statusgrid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Cross-session status-visibility reference app.
 *
 * <p>The {@code @Juke} seam records globally and replays per cookie
 * session. Many sessions can replay the same recording concurrently, each at
 * its own position; {@code GET /service/sessions} reports every active
 * session's {@code lastCall} and {@code percentComplete} — the data a live
 * multi-worker status grid renders.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.example.statusgrid"})
public class StatusGridApplication {

    public static void main(String[] args) {
        SpringApplication.run(StatusGridApplication.class, args);
    }
}
