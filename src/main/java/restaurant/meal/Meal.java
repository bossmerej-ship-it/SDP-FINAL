package restaurant.meal;

import java.math.BigDecimal;

// Product in Factory Method and Component in Decorator.
public interface Meal {
    String getDescription();
    BigDecimal getPrice();
}
