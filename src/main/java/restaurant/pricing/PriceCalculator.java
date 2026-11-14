package restaurant.pricing;

import java.math.BigDecimal;
import restaurant.order.Order;
import restaurant.order.OrderItem;

// Strategy Context. Each request selects its own strategy.
public final class PriceCalculator {
    private final DiscountStrategy strategy;

    public PriceCalculator(DiscountStrategy strategy) {
        this.strategy = java.util.Objects.requireNonNull(strategy);
    }

    public PriceBreakdown calculate(Order order) {
        BigDecimal subtotal = new BigDecimal("0.00");
        for (OrderItem item : order.items()) {
            subtotal = subtotal.add(item.lineTotal());
        }
        BigDecimal discount = strategy.discountFor(subtotal);
        return new PriceBreakdown(subtotal, discount, subtotal.subtract(discount));
    }
}
