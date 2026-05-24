package com.example.exceptions;

import org.juke.framework.annotation.JukeIgnorable;

/**
 * The payload sent across the {@code @Juke} seam to the OMS
 * ({@link IOrderManagementSystem#submitOrder}). It is therefore captured into
 * the recording's {@code .args.json} sidecar and compared on replay.
 *
 * <p>{@link #confirmationNumber} is generated fresh on every attempt, so it
 * differs between the record run and each replay run. Marking it
 * {@link JukeIgnorable} tells Juke to skip the field when it compares the live
 * arguments against the recording — without it, every deterministic replay
 * would be reported as {@code COMPLETED_WITH_DEVIATIONS} purely because of the
 * changing number. This is the textbook use of the annotation: a generated id.
 */
public class OmsOrderRequest {

    private String sku;
    private int quantity;

    @JukeIgnorable
    private String confirmationNumber;

    public OmsOrderRequest() {
    }

    public OmsOrderRequest(String sku, int quantity, String confirmationNumber) {
        this.sku = sku;
        this.quantity = quantity;
        this.confirmationNumber = confirmationNumber;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getConfirmationNumber() {
        return confirmationNumber;
    }

    public void setConfirmationNumber(String confirmationNumber) {
        this.confirmationNumber = confirmationNumber;
    }
}
