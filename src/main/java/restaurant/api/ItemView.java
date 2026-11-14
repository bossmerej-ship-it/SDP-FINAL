package restaurant.api;

import java.math.BigDecimal;

public record ItemView(String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
