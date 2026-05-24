package org.juke.framework.support;

/**
 * Test interface representing billing operations.
 */
public interface IBillable {

    /**
     * Creates an invoice for the given amount.
     * @return invoice description string
     */
    String bill(double amount);

    /**
     * Returns the billing currency.
     */
    String currency();
}

