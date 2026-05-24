package com.example.greeting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Basic Juke record/replay sample: a Spring REST controller serves
 * {@code /greeting?name=…} from a {@code @Juke}-annotated DAO so the
 * upstream service call gets recorded to a ZIP track and replayed on
 * subsequent runs.
 *
 * <p>Component scan covers the framework + Remix REST surface so the
 * {@code /service/*} endpoints (record / replay / session start) are
 * available alongside the app's own {@code /greeting} endpoint. A React
 * SPA bundled into the same jar via {@code frontend-maven-plugin} is
 * served at {@code "/"} by Spring Boot's default static-resource handler.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.example.greeting"})
public class GreetingApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreetingApplication.class, args);
    }
}
