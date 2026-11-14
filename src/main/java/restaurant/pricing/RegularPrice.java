package restaurant.pricing;

import java.math.BigDecimal;

public final class RegularPrice implements DiscountStrategy {
    public BigDecimal discountFor(BigDecimal subtotal) {
        return new BigDecimal("0.00");
    }
}
