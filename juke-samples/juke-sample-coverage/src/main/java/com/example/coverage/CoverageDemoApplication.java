package com.example.coverage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * End-to-end demo of the juke-coverage module.
 *
 * <p>The SPA bundled into this jar contains both a small user journey AND a
 * live coverage dashboard panel. When the JVM is launched with the JaCoCo
 * agent and {@code juke.coverage.enabled=true}, the dashboard polls
 * {@code /service/coverage} and shows both halves climbing as the user clicks
 * through the journey.
 *
 * <p>Component scan covers the framework + Remix REST surface so the
 * {@code /service/*} endpoints are available alongside the app's own
 * {@code /api/*} endpoints.
 *
 * <p>{@code org.juke.coverage} is intentionally excluded from the component
 * scan. {@code juke-coverage} registers itself via Spring Boot auto-configuration
 * (META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports)
 * and is gated on {@code juke.coverage.enabled=true}. Including it in a manual
 * {@code @ComponentScan} would bypass the {@code AutoConfigurationExcludeFilter},
 * causing {@code CoverageAutoConfiguration} to be processed twice and
 * {@code CoverageController} to be registered as both a directly-scanned
 * component and an auto-configuration {@code @Bean}, producing a duplicate-bean
 * conflict at startup.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "org.juke.framework",
        "org.juke.remix",
        "com.example.coverage"})
public class CoverageDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoverageDemoApplication.class, args);
    }
}
