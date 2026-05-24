package org.juke.framework.support;

/**
 * Test interface representing shipping operations.
 */
public interface IShippable {

    /**
     * Ships to the given address.
     * @return tracking number
     */
    String ship(String address);

    /**
     * Returns the estimated delivery days.
     */
    int estimatedDays(String address);
}

