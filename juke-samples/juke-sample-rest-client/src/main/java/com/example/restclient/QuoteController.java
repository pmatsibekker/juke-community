package com.example.restclient;

import com.example.restclient.Quotes.Quote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Business endpoint the UI / tests drive. It fans out to both seams; whether
 * the returned quote ids are live or recorded depends solely on Juke's mode.
 */
@RestController
@RequestMapping("/api")
public class QuoteController {

    private final ShippingClient shipping;
    private final PricingClient pricing;

    public QuoteController(ShippingClient shipping, PricingClient pricing) {
        this.shipping = shipping;
        this.pricing = pricing;
    }

    @GetMapping("/quote/{sku}")
    public Quote quote(@PathVariable String sku) {
        return new Quote(sku, shipping.quote(sku), pricing.quote(sku));
    }
}
