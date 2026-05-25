package com.example.restclient;

import com.example.restclient.Quotes.PriceQuote;
import org.juke.framework.annotation.Juke;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Pricing seam. A second concrete {@code RestTemplate} of the same type as the
 * shipping seam — {@code name = "pricing"} keys its recordings
 * {@code pricing.getForObject.N}, distinct from {@code shipping.*}.
 */
@Service
public class PricingClient {

    @Juke(name = "pricing")
    @Autowired
    @Qualifier("pricingRestTemplate")
    RestTemplate restTemplate;

    @Value("${sample.upstream.base-url:http://localhost:8080}")
    private String baseUrl;

    public PriceQuote quote(String sku) {
        return restTemplate.getForObject(baseUrl + "/upstream/pricing/" + sku, PriceQuote.class);
    }
}
