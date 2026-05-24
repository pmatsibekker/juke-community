package com.example.exceptions;

/**
 * The OMS's response to {@link IOrderManagementSystem#submitOrder}. This is the
 * value Juke records and replays. The {@link #omsOrderId} is issued by the OMS
 * at record time and replayed deterministically, so the same id reappears on
 * every replay run — a visible proof that the upstream response is being served
 * from the recording, not regenerated.
 */
public class OmsReceipt {

    private boolean accepted;
    private String omsOrderId;

    public OmsReceipt() {
    }

    public OmsReceipt(boolean accepted, String omsOrderId) {
        this.accepted = accepted;
        this.omsOrderId = omsOrderId;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getOmsOrderId() {
        return omsOrderId;
    }

    public void setOmsOrderId(String omsOrderId) {
        this.omsOrderId = omsOrderId;
    }
}
