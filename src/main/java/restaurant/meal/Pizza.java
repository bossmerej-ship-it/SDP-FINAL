package restaurant.meal;

import java.math.BigDecimal;

public final class Pizza implements Meal {
    public String getDescription() { return "Pizza"; }
    public BigDecimal getPrice() { return new BigDecimal("3500.00"); }
}
