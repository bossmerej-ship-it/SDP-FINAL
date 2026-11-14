package restaurant.order;

import java.util.ArrayList;
import java.util.List;
import restaurant.meal.Meal;

// Builder creates a validated snapshot of the order's contents.
public final class Order {
    private final String customer;
    private final List<OrderItem> items;
    private final ServiceType serviceType;
    private final Integer tableNumber;
    private final String comment;

    private Order(Builder builder) {
        customer = builder.customer.strip();
        items = List.copyOf(builder.items);
        serviceType = builder.serviceType;
        tableNumber = builder.tableNumber;
        comment = builder.comment.strip();
    }

    public String customer() { return customer; }
    public List<OrderItem> items() { return items; }
    public ServiceType serviceType() { return serviceType; }
    public Integer tableNumber() { return tableNumber; }
    public String comment() { return comment; }

    public static final class Builder {
        private final String customer;
        private final List<OrderItem> items = new ArrayList<>();
        private ServiceType serviceType = ServiceType.TAKEAWAY;
        private Integer tableNumber;
        private String comment = "";

        public Builder(String customer) { this.customer = customer; }

        public Builder addItem(Meal meal, int quantity) {
            items.add(new OrderItem(meal, quantity));
            return this;
        }

        public Builder serviceType(ServiceType value) {
            serviceType = value;
            return this;
        }

        public Builder tableNumber(Integer value) {
            tableNumber = value;
            return this;
        }

        public Builder comment(String value) {
            comment = value == null ? "" : value;
            return this;
        }

        public Order build() {
            if (customer == null || customer.isBlank()) {
                throw new IllegalArgumentException("Enter a customer name.");
            }
            if (items.isEmpty()) {
                throw new IllegalArgumentException("Add at least one meal.");
            }
            if (serviceType == null) {
                throw new IllegalArgumentException("Choose a service type.");
            }
            if (serviceType == ServiceType.DINE_IN && (tableNumber == null || tableNumber <= 0)) {
                throw new IllegalArgumentException(
                        "Dine-in orders require a positive table number.");
            }
            if (serviceType == ServiceType.TAKEAWAY && tableNumber != null) {
                throw new IllegalArgumentException("Takeaway orders cannot have a table number.");
            }
            return new Order(this);
        }
    }
}
