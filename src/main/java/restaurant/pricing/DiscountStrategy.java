package restaurant.pricing;

import java.math.BigDecimal;

public interface DiscountStrategy {
    BigDecimal discountFor(BigDecimal subtotal);
}
