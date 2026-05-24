package com.example.exceptions;

import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Dummy stand-in for the real OMS. It simply issues an order id and accepts the
 * order. In record mode this runs for real and its {@link OmsReceipt} is
 * captured; in replay mode it is the displaced implementation behind the
 * {@code @Juke} seam, so it never executes — which is why {@code juke-coverage}
 * automatically excludes it from the coverage figure (it would otherwise show
 * as 0% through no fault of the developer).
 *
 * <p>It never throws on its own; failures in the demo are injected at runtime
 * by Remix, not produced here.
 */
@Service
public class OrderManagementSystemImpl implements IOrderManagementSystem {

    @Override
    public OmsReceipt submitOrder(OmsOrderRequest request) {
        String omsOrderId = "OMS-" + Long.toHexString(
                ThreadLocalRandom.current().nextLong(0x10_0000, 0xFFF_FFFF)).toUpperCase();
        return new OmsReceipt(true, omsOrderId);
    }
}
