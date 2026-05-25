package com.example.restclient;

import com.example.restclient.Quotes.ShippingQuote;
import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Shipping seam. The concrete {@code RestTemplate} field is wrapped by Juke:
 * <ul>
 *   <li>{@code name = "shipping"} keys its recordings {@code shipping.getForObject.N}
 *       (so it never collides with the pricing seam's {@code RestTemplate});</li>
 *   <li>{@code excludeMethods = {"headForHeaders"}} keeps the health-check call
 *       out of the recording entirely.</li>
 * </ul>
 */
@Service
public class ShippingClient {

    @Juke(name = "shipping", excludeMethods = {"headForHeaders"})
    @Autowired
    @Qualifier("shippingRestTemplate")
    RestTemplate restTemplate;

    @Value("${sample.upstream.base-url:http://localhost:8080}")
    private String baseUrl;

    public ShippingQuote quote(String sku) {
        // Excluded from recording (excludeMethods) — a real call every time.
        restTemplate.headForHeaders(baseUrl + "/upstream/health");
        // Recorded under shipping.getForObject.N; replayed without the upstream.
        return restTemplate.getForObject(baseUrl + "/upstream/shipping/" + sku, ShippingQuote.class);
    }
}
