package com.example.restclient;

/**
 * DTOs exchanged with the (stub) upstream. Each carries a {@code quoteId} that
 * the upstream generates fresh on every live call — so a replayed value is
 * visibly distinguishable from a live one: on replay the recorded quoteId comes
 * back even though the upstream would have produced a new one (or is off).
 */
public final class Quotes {

    private Quotes() {}

    /** Shipping upstream response. */
    public record ShippingQuote(String carrier, int estimatedDays, String quoteId) {}

    /** Pricing upstream response. */
    public record PriceQuote(String currency, double amount, String quoteId) {}

    /** Combined response returned by {@code /api/quote/{sku}}. */
    public record Quote(String sku, ShippingQuote shipping, PriceQuote pricing) {}
}
