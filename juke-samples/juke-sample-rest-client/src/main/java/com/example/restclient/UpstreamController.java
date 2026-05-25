package com.example.restclient;

import com.example.restclient.Quotes.PriceQuote;
import com.example.restclient.Quotes.ShippingQuote;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for the external shipping / pricing services. It lives in the same
 * app purely so the demo is self-contained — to Juke it is a genuine HTTP
 * upstream reached through the {@code RestTemplate} seams.
 *
 * <p>Each response carries a freshly random {@code quoteId}, so you can tell a
 * recorded (replayed) value from a live one at a glance: replay returns the
 * recorded id, a live call returns a new one.
 */
@RestController
@RequestMapping("/upstream")
public class UpstreamController {

    @GetMapping("/shipping/{sku}")
    public ShippingQuote shipping(@PathVariable String sku) {
        return new ShippingQuote("DHL", 1 + ThreadLocalRandom.current().nextInt(5), newId("SHIP"));
    }

    @GetMapping("/pricing/{sku}")
    public PriceQuote pricing(@PathVariable String sku) {
        double amount = 10 + ThreadLocalRandom.current().nextInt(9000) / 100.0;
        return new PriceQuote("USD", amount, newId("PRICE"));
    }

    /** Health endpoint hit via {@code headForHeaders} — excluded from recording. */
    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    private static String newId(String prefix) {
        return prefix + "-" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
    }
}
