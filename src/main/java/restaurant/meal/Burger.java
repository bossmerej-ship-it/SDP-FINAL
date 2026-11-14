package restaurant.meal;

import java.math.BigDecimal;

public final class Burger implements Meal {
    public String getDescription() { return "Burger"; }
    public BigDecimal getPrice() { return new BigDecimal("2500.00"); }
}
