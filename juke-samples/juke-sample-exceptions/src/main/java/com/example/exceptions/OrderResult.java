package com.example.exceptions;

/**
 * Outcome of a single order, returned by {@code POST /api/order}.
 *
 * <ul>
 *   <li>{@code COMPLETED} — the OMS accepted the order; {@link #omsOrderId} is
 *       the recorded/replayed id.</li>
 *   <li>{@code RECORDED} — the OMS call failed (e.g. an injected exception); the
 *       order was captured locally and will be processed later. {@code omsOrderId}
 *       is {@code null}.</li>
 * </ul>
 *
 * <p>There is no {@code QUEUED} value here: "queued" is a purely client-side
 * state the SPA shows when an order is taking too long (the injected delay),
 * before the server has responded. See {@code README.md}.
 */
public record OrderResult(
        String status,
        String confirmationNumber,
        String omsOrderId,
        String sku,
        int quantity,
        String message) {

    public static OrderResult completed(String confirmationNumber, String omsOrderId,
                                        String sku, int quantity) {
        return new OrderResult("COMPLETED", confirmationNumber, omsOrderId, sku, quantity,
                "Order completed.");
    }

    public static OrderResult recorded(String confirmationNumber, String sku, int quantity,
                                       String cause) {
        return new OrderResult("RECORDED", confirmationNumber, null, sku, quantity,
                "We are experiencing technical difficulties. Your order has been recorded "
                        + "and will be processed. (" + cause + ")");
    }
}
