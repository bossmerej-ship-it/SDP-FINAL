package restaurant.order;

import java.math.BigDecimal;
import restaurant.meal.Meal;

public record OrderItem(Meal meal, int quantity) {
    public OrderItem {
        if (meal == null || quantity <= 0) {
            throw new IllegalArgumentException("Choose a meal and a positive quantity.");
        }
    }

    public BigDecimal lineTotal() {
        return meal.getPrice().multiply(BigDecimal.valueOf(quantity));
    }
}
