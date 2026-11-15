package restaurant.api;

import java.util.List;

public record ItemRequest(String meal, List<String> toppings, int quantity) {
    public ItemRequest {
        if (meal == null || meal.isBlank() || quantity <= 0 || toppings == null
                || toppings.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Choose a meal, toppings list and positive quantity.");
        }
        toppings = List.copyOf(toppings);
    }
}
