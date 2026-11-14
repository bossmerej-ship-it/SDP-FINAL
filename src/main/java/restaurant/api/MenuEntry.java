package restaurant.api;

import java.math.BigDecimal;

public record MenuEntry(String code, String description, BigDecimal price) {
}
