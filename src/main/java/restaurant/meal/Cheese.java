package restaurant.meal;

import java.math.BigDecimal;

public final class Cheese extends MealDecorator {
    public Cheese(Meal meal) { super(meal); }

    public String getDescription() { return meal.getDescription() + " + cheese"; }
    public BigDecimal getPrice() { return meal.getPrice().add(new BigDecimal("300.00")); }
}
