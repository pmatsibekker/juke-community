package com.example.controllerdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * {@code @JukeController} reference app.
 *
 * <p>{@link GreetController} is annotated {@code @JukeController}, so the
 * framework's AOP advice captures its request + response on <b>record</b> and,
 * on <b>replay</b>, diffs each call against the recorded baseline — logging a
 * {@code CONTROLLER_MISMATCH} when the live call deviates (the request never
 * aborts). This makes contract drift at the controller boundary visible without
 * any per-call assertions.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.example.controllerdemo"})
public class ControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ControllerApplication.class, args);
    }
}
