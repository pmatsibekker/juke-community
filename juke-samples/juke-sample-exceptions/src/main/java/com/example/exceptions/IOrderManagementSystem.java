package com.example.exceptions;

import java.io.IOException;

/**
 * The external Order Management System, as seen by this application. This
 * interface is the Juke seam: {@link OrderService} holds it behind a
 * {@code @Juke} field, so in record mode real calls are captured and in replay
 * mode the recorded {@link OmsReceipt}s are served back without the real OMS
 * ever being touched.
 *
 * <p>The method declares {@code throws IOException} so that Remix can inject a
 * checked {@code IOException} on a chosen call during replay (run 4), exercising
 * the application's real error-handling branch.
 */
public interface IOrderManagementSystem {

    OmsReceipt submitOrder(OmsOrderRequest request) throws IOException;
}
