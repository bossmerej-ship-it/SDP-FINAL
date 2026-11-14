package restaurant;

import restaurant.meal.*;
import restaurant.order.*;
import restaurant.pricing.*;
import restaurant.api.*;
import restaurant.observer.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.math.BigDecimal;

public final class RestaurantTests {
    private static int passed;

    public static void main(String[] args) {
        check("Factory products and stacked toppings", () -> {
            Meal meal = new Sauce(new Cheese(new BurgerCreator().createMeal()));
            equal(new BigDecimal("3000.00"), meal.getPrice());
            equal("Burger + cheese + sauce", meal.getDescription());
            equal(new BigDecimal("3500.00"), new PizzaCreator().createMeal().getPrice());
        });
        check("Builder snapshots and quantity pricing", () -> {
            Order.Builder builder = new Order.Builder("Aida").addItem(new Burger(), 2);
            Order first = builder.build();
            builder.addItem(new Pizza(), 1);
            equal(1, first.items().size());
            equal(new BigDecimal("5000.00"), new PriceCalculator(new RegularPrice()).calculate(first).total());
        });
        check("Strategies use the same order", () -> {
            Order order = new Order.Builder("Aida").addItem(new Sauce(new Cheese(new Burger())), 1).build();
            equal(new BigDecimal("3000.00"), new PriceCalculator(new RegularPrice()).calculate(order).total());
            PriceBreakdown discounted = new PriceCalculator(new PercentageDiscount(10)).calculate(order);
            equal(new BigDecimal("300.00"), discounted.discount());
            equal(new BigDecimal("2700.00"), discounted.total());
        });
        check("Discount rounds to currency precision", () -> {
            equal(new BigDecimal("0.01"), new PercentageDiscount(10).discountFor(new BigDecimal("0.05")));
            reject(() -> new PercentageDiscount(-1));
            reject(() -> new PercentageDiscount(101));
        });
        check("Builder rejects empty orders and invalid values", () -> {
            reject(() -> new Order.Builder("Aida").build());
            reject(() -> new Order.Builder(" ").addItem(new Burger(), 1).build());
            reject(() -> new Order.Builder("Aida").addItem(new Burger(), 0));
            reject(() -> new Order.Builder("Aida").addItem(new Burger(), -1));
            reject(() -> new Order.Builder("Aida").addItem(new Burger(), 1).serviceType(ServiceType.DINE_IN).build());
            reject(() -> new Order.Builder("Aida").addItem(new Burger(), 1).serviceType(ServiceType.DINE_IN).tableNumber(0).build());
            Order dineIn = new Order.Builder("Aida").addItem(new Burger(), 1)
                    .serviceType(ServiceType.DINE_IN).tableNumber(5).build();
            equal(5, dineIn.tableNumber());
        });
        check("Facade quote and submission agree", () -> {
            RestaurantFacade app = new RestaurantFacade();
            OrderRequest request = request("student");
            equal(new BigDecimal("2700.00"), app.quote(request).total());
            equal(0, app.listOrders().size());
            OrderView placed = app.placeOrder(request);
            equal(app.quote(request), placed.price());
            equal(placed, app.getOrder(placed.id()));
            equal(1, app.listOrders().size());
        });
        check("Facade rejects invalid requests before saving", () -> {
            RestaurantFacade app = new RestaurantFacade();
            reject(() -> app.placeOrder(null));
            reject(() -> app.placeOrder(request("wrong")));
            reject(() -> app.placeOrder(new OrderRequest("Aida", ServiceType.TAKEAWAY, null, "", "regular",
                    List.of(new ItemRequest("unknown", List.of(), 1)))));
            reject(() -> app.placeOrder(new OrderRequest("Aida", ServiceType.TAKEAWAY, null, "", "regular",
                    List.of(new ItemRequest("burger", List.of("unknown"), 1)))));
            reject(() -> new ItemRequest("burger", List.of(), 0));
            reject(() -> new ItemRequest(null, List.of(), 1));
            reject(() -> new OrderRequest("Aida", null, null, "", "regular", List.of()));
            equal(0, app.listOrders().size());
        });
        check("Request lists cannot be changed by the caller", () -> {
            List<String> toppings = new ArrayList<>(List.of("cheese"));
            ItemRequest item = new ItemRequest("burger", toppings, 1);
            toppings.clear();
            List<ItemRequest> items = new ArrayList<>(List.of(item));
            OrderRequest request = new OrderRequest("Aida", ServiceType.TAKEAWAY, null, null, "regular", items);
            items.clear();
            equal(new BigDecimal("2800.00"), new RestaurantFacade().quote(request).total());
        });
        check("Order statuses and snapshots", () -> {
            RestaurantFacade app = new RestaurantFacade();
            OrderView first = app.placeOrder(request("regular"));
            reject(() -> app.updateStatus(first.id(), OrderStatus.READY));
            equal(OrderStatus.NEW, app.getOrder(first.id()).status());
            app.updateStatus(first.id(), OrderStatus.PREPARING);
            equal(OrderStatus.READY, app.updateStatus(first.id(), OrderStatus.READY).status());
            equal(OrderStatus.NEW, first.status());
            reject(() -> app.updateStatus(first.id(), OrderStatus.NEW));
            reject(() -> app.updateStatus(first.id(), null));
            reject(() -> app.getOrder("missing"));
        });
        check("Observers subscribe, filter and unsubscribe", () -> {
            RestaurantFacade app = new RestaurantFacade();
            KitchenObserver kitchen = new KitchenObserver();
            CustomerObserver aida = new CustomerObserver("Aida");
            CustomerObserver other = new CustomerObserver("Other");
            app.subscribe(kitchen);
            app.subscribe(kitchen);
            app.subscribe(aida);
            app.subscribe(other);
            OrderView order = app.placeOrder(request("regular"));
            equal(1, kitchen.messages().size());
            equal(1, aida.messages().size());
            equal(0, other.messages().size());
            app.updateStatus(order.id(), OrderStatus.PREPARING);
            app.updateStatus(order.id(), OrderStatus.PREPARING);
            equal(2, aida.messages().size());
            app.unsubscribe(aida);
            app.updateStatus(order.id(), OrderStatus.READY);
            equal(2, aida.messages().size());
        });
        check("Failing observer cannot break a submitted order", () -> {
            RestaurantFacade app = new RestaurantFacade();
            app.subscribe(order -> { throw new IllegalStateException("Test listener failure"); });
            CustomerObserver customer = new CustomerObserver("Aida");
            app.subscribe(customer);
            OrderView order = app.placeOrder(request("regular"));
            equal(order, app.getOrder(order.id()));
            equal(1, customer.messages().size());
        });
        check("Observer callbacks cannot recursively change order state", () -> {
            RestaurantFacade app = new RestaurantFacade();
            app.subscribe(order -> {
                if (order.status() == OrderStatus.NEW) {
                    app.updateStatus(order.id(), OrderStatus.PREPARING);
                }
            });
            List<OrderStatus> received = new ArrayList<>();
            app.subscribe(order -> received.add(order.status()));
            OrderView order = app.placeOrder(request("regular"));
            equal(List.of(OrderStatus.NEW), received);
            equal(OrderStatus.NEW, app.getOrder(order.id()).status());
            app.updateStatus(order.id(), OrderStatus.PREPARING);
            equal(List.of(OrderStatus.NEW, OrderStatus.PREPARING), received);
        });
        check("Parallel requests preserve unique orders", () -> {
            RestaurantFacade app = new RestaurantFacade();
            var pool = Executors.newFixedThreadPool(4);
            try {
                List<Callable<String>> tasks = new ArrayList<>();
                for (int i = 0; i < 40; i++) {
                    tasks.add(() -> app.placeOrder(request("student")).id());
                }
                var ids = new HashSet<String>();
                for (var result : pool.invokeAll(tasks)) {
                    ids.add(result.get());
                }
                equal(40, ids.size());
                equal(40, app.listOrders().size());
            } catch (Exception error) {
                throw new AssertionError(error);
            } finally {
                pool.shutdownNow();
            }
        });
        System.out.println("RESULT: " + passed + " tests passed");
    }

    private static OrderRequest request(String discount) {
        return new OrderRequest("Aida", ServiceType.TAKEAWAY, null, "Please pack napkins", discount,
                List.of(new ItemRequest("burger", List.of("cheese", "sauce"), 1)));
    }

    private static void check(String name, Runnable action) {
        action.run();
        passed++;
        System.out.println("PASS: " + name);
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }
}
