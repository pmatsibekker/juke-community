package com.example.exceptions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Exception/fault-flow demo for Juke.
 *
 * <p>A shopper buys a SKU; {@link OrderService} places three orders with an
 * external Order Management System (OMS) that is reached through a {@code @Juke}
 * seam ({@link IOrderManagementSystem}). The bundled SPA drives four runs:
 * record the happy path, deterministic replay, replay with an injected delay on
 * the second order ("queued"), and replay with an injected exception on the
 * second order ("technical difficulties"). See {@code README.md}.
 *
 * <p>The component scan covers the framework and the Remix REST surface so the
 * {@code /service/*} control endpoints sit alongside this app's {@code /api/*}.
 * {@code org.juke.coverage} is intentionally NOT scanned: it registers itself
 * via Spring Boot auto-configuration and including it here would double-register
 * its beans.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "org.juke.framework",
        "org.juke.remix",
        "com.example.exceptions"})
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
