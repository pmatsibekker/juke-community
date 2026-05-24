package org.juke.framework.support;

/**
 * Concrete service implementing two separate interfaces.
 * When annotated with {@code @Juke} at the type level, ALL methods from both
 * interfaces are recorded/replayed under the {@code OrderService} identity —
 * never under {@code IBillable} or {@code IShippable}.
 */
public class OrderService implements IBillable, IShippable {

    @Override
    public String bill(double amount) {
        return "INV-" + (int) amount;
    }

    @Override
    public String currency() {
        return "USD";
    }

    @Override
    public String ship(String address) {
        return "TRACK-" + address.hashCode();
    }

    @Override
    public int estimatedDays(String address) {
        return address.length() % 7 + 1;
    }
}

