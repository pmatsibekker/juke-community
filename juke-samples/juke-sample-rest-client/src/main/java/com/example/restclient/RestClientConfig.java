package com.example.restclient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Two distinct {@code RestTemplate} beans of the same type — exactly the case
 * the {@code @Juke(name = ...)} attribute exists for. Without a name both seams
 * would key recordings as {@code RestTemplate.*} and collide.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate shippingRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestTemplate pricingRestTemplate() {
        return new RestTemplate();
    }
}
