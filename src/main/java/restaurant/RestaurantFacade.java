package restaurant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import restaurant.api.*;
import restaurant.meal.*;
import restaurant.observer.OrderObserver;
import restaurant.order.*;
import restaurant.pricing.*;
import restaurant.service.OrderService;

// The public entry point used by the demo and a future web controller.
public final class RestaurantFacade {
    private final Map<String, MealCreator> creators = new LinkedHashMap<>();
    private final OrderService orders = new OrderService();

    public RestaurantFacade() {
        creators.put("burger", new BurgerCreator());
        creators.put("pizza", new PizzaCreator());
    }

    public List<MenuEntry> getMenu() {
        List<MenuEntry> menu = new ArrayList<>();
        for (var entry : creators.entrySet()) {
            Meal meal = entry.getValue().createMeal();
            menu.add(new MenuEntry(entry.getKey(), meal.getDescription(), meal.getPrice()));
        }
        return List.copyOf(menu);
    }

    public PriceBreakdown quote(OrderRequest request) {
        Order order = buildOrder(request);
        return calculatorFor(request.discount()).calculate(order);
    }

    public OrderView placeOrder(OrderRequest request) {
        Order order = buildOrder(request);
        PriceBreakdown price = calculatorFor(request.discount()).calculate(order);
        return orders.submit(order, price);
    }

    public OrderView getOrder(String id) { return orders.getOrder(id); }
    public List<OrderView> listOrders() { return orders.listOrders(); }
    public OrderView updateStatus(String id, OrderStatus next) { return orders.updateStatus(id, next); }
    public void subscribe(OrderObserver observer) { orders.subscribe(observer); }
    public void unsubscribe(OrderObserver observer) { orders.unsubscribe(observer); }

    private Order buildOrder(OrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Order request is required.");
        }
        Order.Builder builder = new Order.Builder(request.customer())
                .serviceType(request.serviceType())
                .tableNumber(request.tableNumber())
                .comment(request.comment());
        for (ItemRequest item : request.items()) {
            MealCreator creator = creators.get(item.meal());
            if (creator == null) {
                throw new IllegalArgumentException("Unknown meal: " + item.meal());
            }
            Meal meal = creator.createMeal(); // Factory Method
            for (String topping : item.toppings()) {
                meal = switch (topping) { // Decorator
                    case "cheese" -> new Cheese(meal);
                    case "sauce" -> new Sauce(meal);
                    default -> throw new IllegalArgumentException("Unknown topping: " + topping);
                };
            }
            builder.addItem(meal, item.quantity());
        }
        return builder.build();
    }

    private PriceCalculator calculatorFor(String discount) {
        DiscountStrategy strategy = switch (discount) {
            case "regular" -> new RegularPrice();
            case "student" -> new PercentageDiscount(10);
            default -> throw new IllegalArgumentException("Unknown discount: " + discount);
        };
        return new PriceCalculator(strategy);
    }
}
