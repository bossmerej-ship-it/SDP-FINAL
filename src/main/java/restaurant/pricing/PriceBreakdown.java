package restaurant.pricing;

import java.math.BigDecimal;

public record PriceBreakdown(BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
}
