package restaurant.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PercentageDiscount implements DiscountStrategy {
    private final int percent;

    public PercentageDiscount(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Discount must be between 0 and 100 percent.");
        }
        this.percent = percent;
    }

    public BigDecimal discountFor(BigDecimal subtotal) {
        return subtotal.multiply(BigDecimal.valueOf(percent))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
