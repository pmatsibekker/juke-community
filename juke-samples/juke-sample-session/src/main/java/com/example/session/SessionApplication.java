package com.example.session;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Session-replay sample: a Spring REST controller exposes
 * {@code /session-greeting?name=…} backed by a {@code @Juke("none")}
 * lazy proxy. With JUKE_SESSION_ID + JUKE_TRACK cookies present, the
 * proxy routes to {@code SessionAwareReplayHandler} and serves
 * responses from the session-specific ZIP recording. Without cookies,
 * "none" opts out of global replay and falls through to the real
 * service.
 *
 * <p>Playwright specs under {@code src/test/playwright/} drive the
 * cookie-isolation scenarios — concurrent browser contexts each have
 * their own track without restarting the JVM.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.example.session"})
public class SessionApplication {

    public static void main(String[] args) {
        SpringApplication.run(SessionApplication.class, args);
    }
}
