package restaurant;

import java.util.List;
import restaurant.api.*;
import restaurant.observer.*;
import restaurant.order.*;
import restaurant.pricing.PriceBreakdown;

// Presentation only. Business classes never read input or print receipts.
public final class Demo {
    public static void main(String[] args) {
        RestaurantFacade app = new RestaurantFacade();
        KitchenObserver kitchen = new KitchenObserver();
        CustomerObserver customer = new CustomerObserver("Aida");
        app.subscribe(kitchen);
        app.subscribe(customer);

        System.out.println("RESTAURANT ORDERING SYSTEM");
        System.out.println("Menu");
        for (MenuEntry entry : app.getMenu()) {
            System.out.println("  " + entry.description() + ": " + entry.price() + " KZT");
        }

        List<ItemRequest> items = List.of(new ItemRequest("burger", List.of("cheese", "sauce"), 1));
        OrderRequest regular = new OrderRequest("Aida", ServiceType.TAKEAWAY, null,
                "Please pack napkins", "regular", items);
        OrderRequest student = new OrderRequest("Aida", ServiceType.TAKEAWAY, null,
                "Please pack napkins", "student", items);

        System.out.println("\nSame order, two pricing strategies");
        System.out.println("  Regular: " + app.quote(regular).total() + " KZT");
        System.out.println("  Student: " + app.quote(student).total() + " KZT");
        OrderView order = app.placeOrder(student);
        printReceipt(order);

        System.out.println("\nKitchen notification");
        kitchen.messages().forEach(System.out::println);
        app.updateStatus(order.id(), OrderStatus.PREPARING);
        app.updateStatus(order.id(), OrderStatus.READY);
        System.out.println("\nCustomer notifications");
        customer.messages().forEach(System.out::println);

        System.out.println("\nValidation example");
        try {
            app.updateStatus(order.id(), OrderStatus.NEW);
        } catch (IllegalArgumentException error) {
            System.out.println("  " + error.getMessage());
        }

        OrderRequest dineIn = new OrderRequest("Daniyar", ServiceType.DINE_IN, 5, "",
                "regular", List.of(new ItemRequest("pizza", List.of("cheese"), 2)));
        OrderView second = app.placeOrder(dineIn);
        System.out.println("\nDine-in order: table " + second.tableNumber()
                + ", 2 pizzas with cheese, " + second.price().total() + " KZT");
        System.out.println("Stored orders: " + app.listOrders().size());
    }

    private static void printReceipt(OrderView order) {
        System.out.println("\nReceipt for " + order.customer() + " | " + order.serviceType());
        for (ItemView item : order.items()) {
            System.out.println("  " + item.quantity() + " x " + item.description() + ": " + item.lineTotal() + " KZT");
        }
        PriceBreakdown price = order.price();
        System.out.println("  Subtotal: " + price.subtotal() + " KZT");
        System.out.println("  Discount: " + price.discount() + " KZT");
        System.out.println("  Total:    " + price.total() + " KZT");
        System.out.println("  Comment:  " + order.comment());
    }
}
