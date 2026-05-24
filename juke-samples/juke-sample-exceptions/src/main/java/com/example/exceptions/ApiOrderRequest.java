package com.example.exceptions;

import org.juke.framework.annotation.JukeIgnorable;

/**
 * Request body for {@code POST /api/order}. The SPA generates
 * {@code confirmationNumber} client-side (so it can show it even when the
 * server response is delayed or fails) and sends it along; the server threads
 * it through to the OMS request where it is also {@code @JukeIgnorable}.
 *
 * <p>The controller-capture request sidecar diffs the inbound request body
 * across runs — without this annotation the freshly generated confirmation
 * number on every replay would surface as a {@code REQ[...]} mismatch.
 */
public record ApiOrderRequest(String sku, int quantity, @JukeIgnorable String confirmationNumber) {
}
