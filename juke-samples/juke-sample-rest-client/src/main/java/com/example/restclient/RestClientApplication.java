package com.example.restclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Concrete-field {@code @Juke} reference app.
 *
 * <p>{@link ShippingClient} and {@link PricingClient} each hold a
 * <b>concrete</b> {@code RestTemplate} field annotated with
 * {@code @Juke(name = ...)}. Juke wraps each field in a CGLIB subclass that
 * delegates to the real {@code RestTemplate}; in <b>record</b> mode the HTTP
 * responses from the (co-located) {@link UpstreamController} are captured, and
 * in <b>replay</b> mode they are returned without any HTTP call.
 *
 * <p>Two things this sample makes visible:
 * <ul>
 *   <li><b>name disambiguation</b> — two {@code RestTemplate} seams of the same
 *       type would both key as {@code RestTemplate.*} and collide; the
 *       {@code name} attribute keys them as {@code shipping.*} / {@code pricing.*}.</li>
 *   <li><b>excludeMethods</b> — the shipping seam's health-check call
 *       ({@code headForHeaders}) is excluded, so it is never recorded.</li>
 * </ul>
 *
 * <p>Note: the concrete-field path follows the <em>global</em> mode (it is not
 * cookie-session-aware — that is the Phase-2 enhancement), so this sample uses
 * global record → replay via {@code /service/record/*} and {@code /service/replay/*},
 * not per-session cookies.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"org.juke.framework", "org.juke.remix", "com.example.restclient"})
public class RestClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestClientApplication.class, args);
    }
}
