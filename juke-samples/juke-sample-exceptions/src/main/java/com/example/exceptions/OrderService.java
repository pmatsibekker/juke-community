package com.example.exceptions;

import org.juke.framework.annotation.Juke;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Places orders with the OMS through the {@code @Juke} seam.
 *
 * <p>The {@code @Juke("juke")} field follows the global mode when no session
 * cookie is present (so {@code /service/record/start} captures real calls) and
 * routes to the per-session replay handler when a {@code JUKE_SESSION} cookie is
 * present (so each replay run is its own isolated session with its own report).
 *
 * <p>{@link #placeOrder} has two branches that matter for the demo and for
 * coverage: the success path and the catch path that turns an OMS failure into
 * a recorded-for-later result. Both are exercised across the four runs.
 */
@Service
public class OrderService {

    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    @Juke("juke")
    private IOrderManagementSystem oms;

    @Autowired
    public OrderService(IOrderManagementSystem oms) {
        this.oms = oms;
    }

    public OrderResult placeOrder(String sku, int quantity, String confirmationNumber) {
        OmsOrderRequest request = new OmsOrderRequest(sku, quantity, confirmationNumber);
        try {
            OmsReceipt receipt = oms.submitOrder(request);
            LOG.info("OMS accepted order {} -> {}", confirmationNumber, receipt.getOmsOrderId());
            return OrderResult.completed(confirmationNumber, receipt.getOmsOrderId(), sku, quantity);
        } catch (Exception e) {
            // The OMS failed (in the demo, an exception injected by Remix on the
            // second order). Real systems still owe the customer an answer, so we
            // record the order locally for asynchronous processing and tell the
            // UI to show the "technical difficulties" message — the customer's
            // confirmation number still stands.
            LOG.warn("OMS call failed for {}; recording order for later processing: {}",
                    confirmationNumber, e.toString());
            return OrderResult.recorded(confirmationNumber, sku, quantity, e.getClass().getSimpleName());
        }
    }

    // ── Admin operations — intentionally NOT exercised by the demo flow, so the
    //    server line/branch coverage figure stays realistically below 100% and
    //    the coverage threshold gate has something meaningful to evaluate. ──

    /** Admin-only: cancel a previously placed order. Unused by the demo. */
    public String cancelOrder(String omsOrderId) {
        if (omsOrderId == null || omsOrderId.isBlank()) {
            return "no order id supplied";
        }
        return "cancellation requested for " + omsOrderId;
    }

    /** Admin-only: reconcile the local ledger against the OMS. Unused by the demo. */
    public String reconcile(int days) {
        StringBuilder report = new StringBuilder("reconciliation over ").append(days).append(" day(s):\n");
        for (int i = 0; i < days; i++) {
            report.append("  day ").append(i).append(" — balanced\n");
        }
        return report.toString();
    }
}
