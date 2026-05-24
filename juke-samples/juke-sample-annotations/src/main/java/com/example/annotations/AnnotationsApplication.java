package com.example.annotations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Reference module for {@code @Juke} usage patterns. Boots a Spring
 * context so the {@code @Component} examples become live beans — read
 * the source, optionally attach a debugger, see the
 * {@code JukeBeanPostProcessor} replace the annotated fields with
 * proxies at startup.
 *
 * <p>No HTTP surface: this module deliberately doesn't pull in
 * spring-boot-starter-web. It's wiring reference, not a service.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "com.example.annotations"})
public class AnnotationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnnotationsApplication.class, args);
    }
}
