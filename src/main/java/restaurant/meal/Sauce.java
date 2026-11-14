package restaurant.meal;

import java.math.BigDecimal;

public final class Sauce extends MealDecorator {
    public Sauce(Meal meal) { super(meal); }

    public String getDescription() { return meal.getDescription() + " + sauce"; }
    public BigDecimal getPrice() { return meal.getPrice().add(new BigDecimal("200.00")); }
}
