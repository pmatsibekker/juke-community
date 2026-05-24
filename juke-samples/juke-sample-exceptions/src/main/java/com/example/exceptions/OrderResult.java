package com.example.exceptions;

import org.juke.framework.annotation.JukeIgnorable;
import org.juke.framework.annotation.JukeIgnorable.IgnoreStrategy;

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
 *
 * <p>{@code confirmationNumber} echoes the per-request client-generated id;
 * {@code omsOrderId} is freshly generated on every successful OMS call but
 * the recorded value is what comes back during REPLAY. Both vary across
 * runs in a way that's not part of the contract under test, so they're
 * marked {@code @JukeIgnorable} for the controller-capture response diff.
 * {@code omsOrderId} uses {@code NOT_NULL} so that a missing value (e.g. on
 * a {@code RECORDED} outcome) still surfaces a structural diff.
 */
public record OrderResult(
        String status,
        @JukeIgnorable String confirmationNumber,
        @JukeIgnorable(strategy = IgnoreStrategy.NOT_NULL) String omsOrderId,
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
