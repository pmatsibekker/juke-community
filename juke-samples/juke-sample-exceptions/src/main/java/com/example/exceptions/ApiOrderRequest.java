package com.example.exceptions;

/**
 * Request body for {@code POST /api/order}. The SPA generates
 * {@code confirmationNumber} client-side (so it can show it even when the
 * server response is delayed or fails) and sends it along; the server threads
 * it through to the OMS request where it is {@code @JukeIgnorable}.
 */
public record ApiOrderRequest(String sku, int quantity, String confirmationNumber) {
}
